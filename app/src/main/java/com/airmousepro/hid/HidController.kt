package com.airmousepro.hid

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/** Registers this phone as a Bluetooth HID device and sends mouse/keyboard/media reports to the connected host. */
@SuppressLint("MissingPermission")
class HidController(private val context: Context) {
    enum class State { Idle, Registering, Ready, Connecting, Connected, Unsupported, Failed }

    private val stateHolder = mutableStateOf(State.Idle)

    /** Plain-coroutine view of [state] for code that runs outside Compose, such as the overlay service. */
    val stateFlow = MutableStateFlow(State.Idle)

    var state: State
        get() = stateHolder.value
        private set(v) { stateHolder.value = v; stateFlow.value = v }

    var hostName by mutableStateOf<String?>(null); private set

    /** Address of the connected device, or null. Read it together with [hostName] so Compose sees changes. */
    val hostAddress: String? get() = if (hostName != null) host?.address else null

    private var autoReconnectTried = false
    private var autoAttempt = false

    /** Progress of the launch-time search for a recently used device. */
    sealed interface AutoStatus {
        data object Idle : AutoStatus
        data class Trying(val name: String, val index: Int, val total: Int) : AutoStatus
        data object Done : AutoStatus
    }

    var autoStatus: AutoStatus by mutableStateOf(AutoStatus.Idle); private set

    private val main = Handler(Looper.getMainLooper())
    private var autoQueue = ArrayDeque<BluetoothDevice>()
    private var autoTotal = 0
    private var autoCurrent: BluetoothDevice? = null
    private var connectingTo: BluetoothDevice? = null
    private val autoTimeout = Runnable { advanceAuto() }

    @Volatile var gamepadSent = 0
    @Volatile var gamepadFailed = 0

    /** Name of a device that did not accept our last connection attempt, until dismissed or connected. */
    var connectFailure by mutableStateOf<String?>(null); private set

    fun dismissFailure() { connectFailure = null }

    private val adapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    private val executor = Executors.newSingleThreadExecutor()
    // Read by the sender thread, written by Bluetooth callbacks.
    @Volatile private var hid: BluetoothHidDevice? = null
    @Volatile private var host: BluetoothDevice? = null
    // Touched only on the sender thread.
    private var mouseButtons = 0

    /** Timestamped HID events, shown under Settings → Developer options. */
    var log by mutableStateOf(listOf<String>()); private set

    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private var failedSends = 0

    @Synchronized
    private fun note(msg: String) {
        Log.d("AirMouseHid", msg)
        log = (log + "${timeFormat.format(Date())}  $msg").takeLast(500)
    }

    fun clearLog() { log = emptyList() }

    /** Environment details that help when debugging Bluetooth issues. */
    fun diagnostics(): String = buildString {
        val pkg = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
        appendLine("AirMouse Pro ${pkg?.versionName} (build ${pkg?.longVersionCode})")
        appendLine("Bluetooth name: ${if (hasPermission()) adapter?.name else "?"}")
        appendLine("Phone: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
        appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), build ${Build.DISPLAY}")
        appendLine("Bluetooth permission: ${hasPermission()}, adapter on: ${bluetoothEnabled()}")
        appendLine("State: $state, host: ${hostName ?: "-"}, proxy: ${hid != null}")
        appendLine("Paired: " + pairedDevices().joinToString { "${it.name} ${it.address} bond=${it.bondState}" })
    }

    private val callback = object : BluetoothHidDevice.Callback() {
        // Hosts such as Windows expect a handshake for these requests; staying silent can make them drop the link.
        override fun onGetReport(device: BluetoothDevice, type: Byte, id: Byte, bufferSize: Int) {
            note("host GET_REPORT type=$type id=$id size=$bufferSize")
            hid?.reportError(device, BluetoothHidDevice.ERROR_RSP_UNSUPPORTED_REQ)
        }

        override fun onSetReport(device: BluetoothDevice, type: Byte, id: Byte, data: ByteArray) {
            note("host SET_REPORT type=$type id=$id data=${data.joinToString("") { "%02x".format(it) }}")
            hid?.reportError(device, BluetoothHidDevice.ERROR_RSP_SUCCESS)
        }

        override fun onSetProtocol(device: BluetoothDevice, protocol: Byte) {
            note("host SET_PROTOCOL $protocol")
        }

        override fun onInterruptData(device: BluetoothDevice, reportId: Byte, data: ByteArray) {
            note("host interrupt data id=$reportId data=${data.joinToString("") { "%02x".format(it) }}")
        }

        override fun onVirtualCableUnplug(device: BluetoothDevice) {
            note("host virtual cable unplug ${device.address}")
        }

        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            note("app registered=$registered plugged=${pluggedDevice?.address}")
            if (registered) {
                if (state != State.Connected) state = State.Ready
                startAutoConnect()
            } else {
                state = State.Failed
            }
        }

        override fun onConnectionStateChanged(device: BluetoothDevice, connState: Int) {
            note("connection ${device.address} state=$connState")
            when (connState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    connectFailure = null
                    host = device
                    hostName = device.name ?: device.address
                    settings.lastHost = device.address
                    settings.recordConnected(device.address)
                    connectingTo = null
                    // Scanning shares the radio with the connection and slows it down, so stop once connected.
                    main.post { stopScan() }
                    // Found one: stop searching the rest of the recent list.
                    main.removeCallbacks(autoTimeout)
                    autoCurrent = null
                    autoQueue.clear()
                    autoStatus = AutoStatus.Done
                    state = State.Connected
                }
                BluetoothProfile.STATE_CONNECTING -> state = State.Connecting
                else -> {
                    // The late "disconnected" for an attempt we already gave up on must not undo the next attempt.
                    val stale = connectingTo != null && device != connectingTo && device != host
                    if (!stale) {
                        // A silent auto-reconnect that finds the host switched off is not worth a warning card.
                        if (state == State.Connecting && !autoAttempt) connectFailure = device.name ?: device.address
                        if (device == host) { host = null; hostName = null }
                        connectingTo = null
                        state = if (hid != null) State.Ready else State.Idle
                        if (device == autoCurrent) main.post { advanceAuto() }
                    }
                }
            }
        }
    }

    private val serviceListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            val h = proxy as BluetoothHidDevice
            hid = h
            note("HID proxy connected")
            val sdp = BluetoothHidDeviceAppSdpSettings(
                "AirMouse Pro", "Phone mouse and keyboard", "AirMousePro",
                BluetoothHidDevice.SUBCLASS1_COMBO, HidDescriptor.bytes,
            )
            // Clear any registration left behind by a previous run of this app.
            h.unregisterApp()
            val ok = h.registerApp(sdp, null, null, executor, callback)
            note("registerApp() returned $ok")
            if (!ok) state = State.Failed
        }

        override fun onServiceDisconnected(profile: Int) {
            note("HID proxy disconnected")
            hid = null
            host = null
            hostName = null
            state = State.Idle
        }
    }

    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val settings get() = (context.applicationContext as com.airmousepro.AirMouseApp).settings

    /**
     * Once per start: tries the recently used devices, newest first, giving each a few seconds. A device that is
     * switched off or out of range simply times out and the next one is tried.
     */
    private fun startAutoConnect() {
        if (autoReconnectTried) return
        autoReconnectTried = true
        if (!settings.autoReconnect || state == State.Connected) {
            autoStatus = AutoStatus.Done
            return
        }
        val bonded = pairedDevices().associateBy { it.address }
        var list = settings.history().mapNotNull { bonded[it.first] }.take(4)
        // Installs from before the history existed only know the last device.
        if (list.isEmpty()) list = listOfNotNull(bonded[settings.lastHost])
        autoQueue = ArrayDeque(list)
        autoTotal = list.size
        if (list.isEmpty()) {
            autoStatus = AutoStatus.Done
            return
        }
        main.post { tryNextAuto() }
    }

    private fun tryNextAuto() {
        main.removeCallbacks(autoTimeout)
        if (state == State.Connected) {
            autoStatus = AutoStatus.Done
            return
        }
        val d = autoQueue.removeFirstOrNull()
        if (d == null) {
            autoCurrent = null
            autoStatus = AutoStatus.Done
            note("auto-connect finished without a connection")
            return
        }
        autoCurrent = d
        autoStatus = AutoStatus.Trying(d.name ?: d.address, autoTotal - autoQueue.size, autoTotal)
        note("auto-connect trying ${d.name} (${autoTotal - autoQueue.size} of $autoTotal)")
        connect(d, auto = true)
        main.postDelayed(autoTimeout, AUTO_TIMEOUT_MS)
    }

    private fun advanceAuto() {
        val d = autoCurrent ?: return
        autoCurrent = null
        main.removeCallbacks(autoTimeout)
        if (state == State.Connecting) runCatching { hid?.disconnect(d) }
        tryNextAuto()
    }

    fun cancelAutoConnect() {
        main.removeCallbacks(autoTimeout)
        autoQueue.clear()
        val d = autoCurrent
        autoCurrent = null
        if (d != null && state == State.Connecting) runCatching { hid?.disconnect(d) }
        autoStatus = AutoStatus.Done
    }

    var showAsAirMouse: Boolean
        get() = prefs.getBoolean(KEY_RENAME, true)
        set(value) {
            prefs.edit().putBoolean(KEY_RENAME, value).apply()
            if (value) applyDisplayName() else restoreName()
        }

    /**
     * Other devices see the phone's Bluetooth name, and Android has no per-app name, so while AirMouse is running
     * the adapter is renamed to "AirMouse - <your name>". The original name is saved and put back on exit.
     */
    fun applyDisplayName() {
        val a = adapter ?: return
        if (!showAsAirMouse || !hasPermission() || !a.isEnabled) return
        val current = a.name ?: return
        val original = if (current.startsWith(NAME_PREFIX)) {
            prefs.getString(KEY_ORIGINAL, null) ?: current.removePrefix(NAME_PREFIX)
        } else {
            current
        }
        prefs.edit().putString(KEY_ORIGINAL, original).apply()
        val target = NAME_PREFIX + original
        if (current != target) note("setName(\"$target\") returned ${a.setName(target)}")
    }

    fun restoreName() {
        val a = adapter ?: return
        if (!hasPermission()) return
        val original = prefs.getString(KEY_ORIGINAL, null) ?: return
        if (a.name?.startsWith(NAME_PREFIX) == true) note("setName(\"$original\") returned ${a.setName(original)}")
    }

    // ---- Finding and pairing new devices ----

    /** A device seen during a scan that this phone is not paired with yet. */
    data class Nearby(val device: BluetoothDevice, val name: String)

    var scanning by mutableStateOf(false); private set
    var nearby by mutableStateOf(listOf<Nearby>()); private set
    /** Address of the device a pairing request is in progress with. */
    var pairingAddress by mutableStateOf<String?>(null); private set
    var pairFailure by mutableStateOf<String?>(null); private set
    fun dismissPairFailure() { pairFailure = null }

    private var scanReceiverRegistered = false
    private val scanTimeout = Runnable { stopScan() }

    private val scanReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val d = IntentCompat.getParcelableExtra(i, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java) ?: return
                    // Devices with no name are almost all Bluetooth LE background noise, so they are left out.
                    val name = i.getStringExtra(BluetoothDevice.EXTRA_NAME) ?: d.name ?: return
                    if (d.bondState == BluetoothDevice.BOND_BONDED) return
                    if (nearby.none { it.device.address == d.address }) nearby = nearby + Nearby(d, name)
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> scanning = false
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    val d = IntentCompat.getParcelableExtra(i, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java) ?: return
                    val now = i.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)
                    val before = i.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.BOND_NONE)
                    if (d.address != pairingAddress) return
                    note("bond ${d.address} $before -> $now")
                    if (now == BluetoothDevice.BOND_BONDED) {
                        pairingAddress = null
                        nearby = nearby.filterNot { it.device.address == d.address }
                        // The pairing was started from this app, so carry on and connect to it.
                        if (state == State.Ready) connect(d)
                    } else if (now == BluetoothDevice.BOND_NONE && before == BluetoothDevice.BOND_BONDING) {
                        pairingAddress = null
                        pairFailure = d.name ?: d.address
                    }
                }
            }
        }
    }

    private fun ensureScanReceiver() {
        if (scanReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        }
        ContextCompat.registerReceiver(context, scanReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        scanReceiverRegistered = true
    }

    /** Scanning needs "Nearby devices" on Android 12 and newer, and Location on older versions. */
    fun hasScanPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else {
            context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }

    /** Looks for devices around the phone for about 20 seconds. Stops any automatic reconnecting first. */
    fun startScan() {
        val a = adapter ?: return
        if (!hasScanPermission() || !hasPermission() || !a.isEnabled) return
        cancelAutoConnect()
        ensureScanReceiver()
        nearby = emptyList()
        pairFailure = null
        if (a.isDiscovering) a.cancelDiscovery()
        scanning = a.startDiscovery()
        note("startDiscovery returned $scanning")
        main.removeCallbacks(scanTimeout)
        main.postDelayed(scanTimeout, 20_000)
    }

    fun stopScan() {
        main.removeCallbacks(scanTimeout)
        val a = adapter
        if (a != null && hasScanPermission() && hasPermission() && a.isDiscovering) a.cancelDiscovery()
        scanning = false
    }

    /** Starts Android's own pairing for a scanned device. When it succeeds the app connects to it. */
    fun pair(n: Nearby) {
        stopScan()
        cancelAutoConnect()
        ensureScanReceiver()
        pairFailure = null
        pairingAddress = n.device.address
        if (!n.device.createBond()) {
            pairingAddress = null
            pairFailure = n.name
        }
    }

    /**
     * Call after Bluetooth permissions are granted. With [autoConnect] false (a manual Refresh) the app registers
     * but does not go off reconnecting to recent devices, which would fight a pairing in progress.
     */
    fun start(autoConnect: Boolean = true) {
        if (hid != null || state == State.Registering) return
        val a = adapter
        if (a == null || !hasPermission() || !a.isEnabled) { state = State.Idle; return }
        applyDisplayName()
        autoReconnectTried = !autoConnect
        state = State.Registering
        val ok = a.getProfileProxy(context, serviceListener, BluetoothProfile.HID_DEVICE)
        note("getProfileProxy(HID_DEVICE) returned $ok")
        if (!ok) state = State.Unsupported
    }

    /** Drops the current registration and registers again, without reconnecting to recent devices. */
    fun restart() {
        stop(restoreName = false)
        start(autoConnect = false)
    }

    fun stop(restoreName: Boolean = true) {
        if (restoreName) restoreName()
        hid?.let { h ->
            h.unregisterApp()
            adapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, h)
        }
        hid = null
        host = null
        hostName = null
        state = State.Idle
    }

    /** Android 12+ throws SecurityException on Bluetooth calls until BLUETOOTH_CONNECT is granted. */
    fun hasPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    fun bluetoothEnabled() = hasPermission() && adapter?.isEnabled == true

    fun pairedDevices(): List<BluetoothDevice> =
        if (hasPermission()) adapter?.bondedDevices?.toList().orEmpty() else emptyList()

    fun connect(device: BluetoothDevice, auto: Boolean = false) {
        val h = hid ?: return
        autoAttempt = auto
        // A manual tap takes over from any automatic search that is still running.
        if (!auto) cancelAutoConnect()
        connectingTo = device
        connectFailure = null
        state = State.Connecting
        val ok = h.connect(device)
        note("connect(${device.address}) returned $ok")
        if (!ok) state = State.Ready
    }

    fun disconnect() {
        host?.let { hid?.disconnect(it) }
    }

    // ---- Sending reports ----
    //
    // Every sendReport() is a blocking call into the Bluetooth service. Making it on the calling thread stalls the
    // touch thread, and sending one report per touch or sensor sample (120 to 250 a second) is more than the link
    // carries, so a queue builds up and the cursor lags, then rushes to catch up. Instead all reports go through one
    // dedicated thread, and pointer movement is merged: the first movement after a pause is sent at once, later
    // movement is summed into at most one report per MOVE_GAP_NS. Nothing is lost, only combined.

    private val sender: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "hid-sender").apply { isDaemon = true; priority = Thread.MAX_PRIORITY }
    }
    private val pendLock = Any()
    private var pendX = 0
    private var pendY = 0
    private var pendWheel = 0
    private var pendPan = 0
    private var moveQueued = false
    @Volatile private var lastMoveNs = 0L
    private var padReport: ByteArray? = null
    private var padQueued = false
    @Volatile private var lastPadNs = 0L

    /** Queues a report behind everything already queued, so presses, keys and releases stay in order. */
    private fun send(id: Int, data: ByteArray) {
        sender.execute { sendNow(id, data) }
    }

    /** Runs on the sender thread only. */
    private fun sendNow(id: Int, data: ByteArray) {
        val d = host ?: return
        val ok = hid?.sendReport(d, id, data) ?: false
        if (id == HidDescriptor.ID_GAMEPAD) {
            if (ok) gamepadSent++ else gamepadFailed++
        }
        if (!ok && failedSends++ < 5) note("sendReport id=$id failed")
    }

    // ---- Mouse ----

    fun moveMouse(dx: Int, dy: Int, wheel: Int = 0, pan: Int = 0) {
        if (dx == 0 && dy == 0 && wheel == 0 && pan == 0) return
        synchronized(pendLock) {
            pendX += dx
            pendY += dy
            pendWheel += wheel
            pendPan += pan
            if (moveQueued) return
            moveQueued = true
            // Zero wait when the link has been quiet, so the first movement never feels delayed.
            val wait = (MOVE_GAP_NS - (System.nanoTime() - lastMoveNs)).coerceAtLeast(0L)
            sender.schedule({ flushMoves() }, wait, TimeUnit.NANOSECONDS)
        }
    }

    /** Runs on the sender thread only. */
    private fun flushMoves() {
        var x: Int
        var y: Int
        var w: Int
        var p: Int
        synchronized(pendLock) {
            x = pendX; y = pendY; w = pendWheel; p = pendPan
            pendX = 0; pendY = 0; pendWheel = 0; pendPan = 0
            moveQueued = false
        }
        if (x == 0 && y == 0 && w == 0 && p == 0) return
        lastMoveNs = System.nanoTime()
        // A report carries one signed byte per axis, so split large moves.
        while (x != 0 || y != 0 || w != 0 || p != 0) {
            val cx = x.coerceIn(-127, 127)
            val cy = y.coerceIn(-127, 127)
            val cw = w.coerceIn(-127, 127)
            val cp = p.coerceIn(-127, 127)
            sendNow(HidDescriptor.ID_MOUSE, byteArrayOf(mouseButtons.toByte(), cx.toByte(), cy.toByte(), cw.toByte(), cp.toByte()))
            x -= cx; y -= cy; w -= cw; p -= cp
        }
    }

    /** Positive [vertical] scrolls up, positive [horizontal] scrolls right, in mouse-wheel notches. */
    fun scroll(vertical: Int, horizontal: Int = 0) = moveMouse(0, 0, vertical, horizontal)

    fun setButton(button: Int, down: Boolean) {
        sender.execute {
            // Movement made before the press goes out first, so a click lands where it was aimed.
            flushMoves()
            mouseButtons = if (down) mouseButtons or button else mouseButtons and button.inv()
            sendNow(HidDescriptor.ID_MOUSE, byteArrayOf(mouseButtons.toByte(), 0, 0, 0, 0))
        }
    }

    fun click(button: Int = BUTTON_LEFT) {
        setButton(button, true)
        setButton(button, false)
    }

    // ---- Keyboard ----

    fun keyCombo(modifiers: Int, vararg usages: Int) {
        val report = ByteArray(8)
        report[0] = modifiers.toByte()
        usages.take(6).forEachIndexed { i, u -> report[2 + i] = u.toByte() }
        send(HidDescriptor.ID_KEYBOARD, report)
        send(HidDescriptor.ID_KEYBOARD, ByteArray(8))
    }

    /** Holds keys down until [keyUp]; used for Ctrl+wheel zoom. */
    fun keyDown(modifiers: Int, vararg usages: Int) {
        val report = ByteArray(8)
        report[0] = modifiers.toByte()
        usages.take(6).forEachIndexed { i, u -> report[2 + i] = u.toByte() }
        send(HidDescriptor.ID_KEYBOARD, report)
    }

    fun keyUp() = send(HidDescriptor.ID_KEYBOARD, ByteArray(8))

    fun layout(): KbLayout = KbLayout.values().getOrElse(settings.kbLayout) { KbLayout.US }

    fun typeChar(c: Char): Boolean {
        val (mod, usage) = KeyMap.forChar(c, layout()) ?: return false
        keyCombo(mod, usage)
        return true
    }

    // ---- Consumer / media ----

    /** Presses and releases one media key (play, volume, brightness, home and so on). */
    fun consumer(usage: Int) {
        send(HidDescriptor.ID_CONSUMER, byteArrayOf((usage and 0xFF).toByte(), (usage shr 8).toByte()))
        send(HidDescriptor.ID_CONSUMER, byteArrayOf(0, 0))
    }

    // ---- Gamepad ----

    /** Buttons are a 16-bit mask, [hat] is 0 for centred or 1..8 clockwise from up, sticks are -127..127, triggers 0..255. */
    fun gamepad(buttons: Int, hat: Int, lx: Int, ly: Int, rx: Int, ry: Int, lt: Int, rt: Int) {
        val data = byteArrayOf(
            (buttons and 0xFF).toByte(), (buttons shr 8).toByte(), hat.toByte(),
            lx.toByte(), ly.toByte(), rx.toByte(), ry.toByte(), lt.toByte(), rt.toByte(),
        )
        // Only the newest state matters, so a backlog is replaced rather than sent in full.
        synchronized(pendLock) {
            padReport = data
            if (padQueued) return
            padQueued = true
            val wait = (MOVE_GAP_NS - (System.nanoTime() - lastPadNs)).coerceAtLeast(0L)
            sender.schedule({ flushPad() }, wait, TimeUnit.NANOSECONDS)
        }
    }

    /** Runs on the sender thread only. */
    private fun flushPad() {
        val data: ByteArray?
        synchronized(pendLock) {
            data = padReport
            padReport = null
            padQueued = false
        }
        lastPadNs = System.nanoTime()
        if (data != null) sendNow(HidDescriptor.ID_GAMEPAD, data)
    }

    companion object {
        /** Shortest gap between merged pointer or gamepad reports: about 110 a second, which a Bluetooth link can carry. */
        private const val MOVE_GAP_NS = 9_000_000L
        private const val AUTO_TIMEOUT_MS = 9_000L
        private const val NAME_PREFIX = "AirMouse - "
        private const val KEY_RENAME = "show_as_airmouse"
        private const val KEY_ORIGINAL = "original_bt_name"
        const val BUTTON_LEFT = 1
        const val BUTTON_RIGHT = 2
        const val BUTTON_MIDDLE = 4
    }
}
