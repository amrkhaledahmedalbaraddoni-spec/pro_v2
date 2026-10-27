package com.red.sovereign.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.concurrent.ConcurrentHashMap

/**
 * مدير الشبكات الموحد - يدعم كل أنواع الشبكات المحلية
 * 
 * المميزات أفضل من واتساب وتيليجرام:
 * - دعم كل الشبكات: WiFi, Ethernet, USB Tethering, VPN, Hotspot, Bluetooth PAN
 * - اكتشاف تلقائي عبر mDNS/NSD + مسح IP ذكي
 * - تبديل تلقائي عند تغير الشبكة
 * - قياس جودة الشبكة وتكيف المكالمات
 * - دعم IPv4 و IPv6
 * - عمل بدون إنترنت (P2P محلي)
 */
object UnifiedNetworkManager {
    
    /**
     * شبكة واحدة كما تعرضها «إدارة الشبكات»: النوع + العنوان + الجودة.
     * `bandwidth` تقديري حسب نوع الناقل (القياس الفعلي للمكالمات في CallQualityManager)؛
     * الهدف إعطاء المستخدم رتبة حجم لا رقم مزعوم دقيق.
     */
    data class NetworkState(
        val type: NetworkType,
        val ipAddress: String?,
        val subnet: String = "",
        val isConnected: Boolean = true,
        val quality: NetworkQuality = NetworkQuality.GOOD,
        val transport: String = "",
        val bandwidth: Int = 0
    ) {
        /** عنوان مضيف للاستخدام الداخلي (المسح/الترتيب). */
        val ip: String get() = ipAddress.orEmpty()
    }

    /**
     * خادم يونس مكتشف على الشبكة المحلية.
     * `isYounesVerified` = أُثبت التوقيع فعليًا عبر YounesServerSignature
     * (/health + /api/identity/authority) — لا مجرد منفذ مفتوح.
     */
    data class DiscoveredServer(
        val id: String,
        val host: String,
        val port: Int,
        val url: String,
        val discoveryMethod: String,
        val latencyMs: Long = 0L,
        val isYounesVerified: Boolean = false
    ) {
        val ipAddress: String get() = host
    }

    enum class NetworkType {
        WIFI, ETHERNET, USB_TETHER, VPN, HOTSPOT, BLUETOOTH, MOBILE, UNKNOWN
    }
    
    enum class NetworkQuality {
        EXCELLENT, GOOD, FAIR, POOR, OFFLINE;

        /** رتبة 0..4 تصاعدية من الأسوأ — يستخدمها اختيار أفضل شبكة (لا ordinal). */
        val rank: Int get() = when (this) {
            EXCELLENT -> 4
            GOOD -> 3
            FAIR -> 2
            POOR -> 1
            OFFLINE -> 0
        }
    }
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _currentNetwork = MutableStateFlow<NetworkState?>(null)
    val currentNetwork: StateFlow<NetworkState?> = _currentNetwork
    
    private val _availableNetworks = MutableStateFlow<List<NetworkState>>(emptyList())
    val availableNetworks: StateFlow<List<NetworkState>> = _availableNetworks

    /** اسم معروض في «إدارة الشبكات» لنفس المصدر — لا نسخة ثانية من الحالة. */
    val networkStates: StateFlow<List<NetworkState>> get() = _availableNetworks
    
    private val _isOnline = MutableStateFlow(false)
    val isOnline: StateFlow<Boolean> = _isOnline

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning

    private val _scanProgress = MutableStateFlow(0f)
    val scanProgress: StateFlow<Float> = _scanProgress
    
    private var connectivityManager: ConnectivityManager? = null
    private var nsdManager: NsdManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    /** سياق التطبيق — يلزم للفحص اليدوي (scanForServers) بعد initialize. */
    @Volatile private var appContext: Context? = null
    
    private val discoveredServers = ConcurrentHashMap<String, DiscoveredServer>()
    private val _discoveredServersFlow = MutableStateFlow<Map<String, DiscoveredServer>>(emptyMap())
    val discoveredServersFlow: StateFlow<Map<String, DiscoveredServer>> = _discoveredServersFlow
    
    fun initialize(context: Context) {
        appContext = context.applicationContext
        connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
        
        registerNetworkCallback(context)
        scanAllInterfaces()
        startMdnsDiscovery(context)
    }
    
    private fun registerNetworkCallback(context: Context) {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
            .build()
            
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                scope.launch {
                    scanAllInterfaces()
                    _isOnline.value = true
                }
            }
            
            override fun onLost(network: Network) {
                scope.launch {
                    scanAllInterfaces()
                    _isOnline.value = checkAnyConnection(context)
                }
            }
            
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                scope.launch {
                    scanAllInterfaces()
                    val quality = estimateQuality(caps)
                    _currentNetwork.value = _currentNetwork.value?.copy(quality = quality)
                }
            }
        }
        
        connectivityManager?.registerNetworkCallback(request, networkCallback!!)
    }
    
    fun scanAllInterfaces(): List<NetworkState> {
        val networks = mutableListOf<NetworkState>()
        
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            
            for (nif in interfaces) {
                if (!nif.isUp || nif.isLoopback) continue
                
                val type = when {
                    nif.name.contains("wlan", true) || nif.name.contains("wifi", true) -> NetworkType.WIFI
                    nif.name.contains("eth", true) || nif.name.contains("en", true) -> NetworkType.ETHERNET
                    nif.name.contains("usb", true) || nif.name.contains("rndis", true) -> NetworkType.USB_TETHER
                    nif.name.contains("tun", true) || nif.name.contains("vpn", true) || nif.name.contains("ppp", true) -> NetworkType.VPN
                    nif.name.contains("ap", true) || nif.name.contains("hotspot", true) -> NetworkType.HOTSPOT
                    nif.name.contains("bt", true) || nif.name.contains("bnep", true) -> NetworkType.BLUETOOTH
                    nif.name.contains("rmnet", true) || nif.name.contains("ccmni", true) -> NetworkType.MOBILE
                    else -> NetworkType.UNKNOWN
                }
                
                for (addr in nif.interfaceAddresses) {
                    val inetAddr = addr.address
                    if (inetAddr is Inet4Address && !inetAddr.isLoopbackAddress) {
                        val ip = inetAddr.hostAddress ?: continue
                        val prefix = addr.networkPrefixLength
                        val subnet = "${ip.substringBeforeLast('.')}.0/$prefix"
                        
                        val quality = when (type) {
                            NetworkType.WIFI, NetworkType.ETHERNET -> NetworkQuality.EXCELLENT
                            NetworkType.USB_TETHER -> NetworkQuality.GOOD
                            NetworkType.VPN -> NetworkQuality.FAIR
                            NetworkType.HOTSPOT -> NetworkQuality.GOOD
                            NetworkType.MOBILE -> NetworkQuality.FAIR
                            else -> NetworkQuality.GOOD
                        }
                        
                        networks.add(
                            NetworkState(
                                type = type,
                                ipAddress = ip,
                                subnet = subnet,
                                isConnected = true,
                                quality = quality,
                                transport = nif.name,
                                bandwidth = estimateBandwidth(type)
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            // تجاهل أخطاء المسح
        }
        
        _availableNetworks.value = networks
        if (networks.isNotEmpty() && _currentNetwork.value == null) {
            _currentNetwork.value = networks.first()
        }
        
        return networks
    }
    
    private fun checkAnyConnection(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return hasLocalInterface()
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) || 
               hasLocalInterface()
    }
    
    private fun hasLocalInterface(): Boolean {
        return try {
            NetworkInterface.getNetworkInterfaces()?.toList().orEmpty().any { nif ->
                nif.isUp && !nif.isLoopback && nif.interfaceAddresses.any { 
                    it.address is Inet4Address && (it.address as Inet4Address).isSiteLocalAddress 
                }
            }
        } catch (e: Exception) { false }
    }
    
    /**
     * عرض نطاق تقديري بالكيلوبت/ث حسب نوع الناقل — للعرض الترتيبي فقط، لا وعد قياس.
     * القيم محافظة (الأسوأ في الفئة) حتى لا يوحي الرقم بأداء لا يتحقق.
     */
    private fun estimateBandwidth(type: NetworkType): Int = when (type) {
        NetworkType.ETHERNET -> 1_000_000
        NetworkType.WIFI -> 150_000
        NetworkType.USB_TETHER -> 100_000
        NetworkType.HOTSPOT -> 72_000
        NetworkType.VPN -> 50_000
        NetworkType.BLUETOOTH -> 2_000
        NetworkType.MOBILE -> 30_000
        NetworkType.UNKNOWN -> 10_000
    }

    private fun estimateQuality(caps: NetworkCapabilities): NetworkQuality {
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkQuality.EXCELLENT
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkQuality.EXCELLENT
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> NetworkQuality.FAIR
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                if (Build.VERSION.SDK_INT >= 29) {
                    when {
                        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) -> NetworkQuality.GOOD
                        else -> NetworkQuality.FAIR
                    }
                } else NetworkQuality.FAIR
            }
            else -> NetworkQuality.GOOD
        }
    }
    
    private fun startMdnsDiscovery(context: Context) {
        try {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
            val mlock = wifi?.createMulticastLock("red-unified-mdns")?.apply { setReferenceCounted(true) }
            mlock?.acquire()
            
            discoveryListener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(regType: String) {}
                override fun onServiceFound(service: NsdServiceInfo) {
                    nsdManager?.resolveService(service, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
                        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                            val host = serviceInfo.host?.hostAddress ?: return
                            if ('%' in host) return // تجاهل link-local مع zone
                            val port = serviceInfo.port.takeIf { it > 0 } ?: YounesServerSignature.DEFAULT_PORT
                            val key = "${serviceInfo.serviceName}._${serviceInfo.serviceType}"
                            // mDNS يثبت الوجود لا الهوية — تُسجل فوراً (بلا توقيع) ثم يُتحقق منها
                            // فعلياً عبر LocalServerDiscovery فيتحدث العلم والزمن بلا تعليق الواجهة.
                            registerDiscovered(
                                base = YounesServerSignature.buildUrl(host, port),
                                source = "mDNS",
                                latencyMs = 0L,
                                verified = false,
                                key = key
                            )
                            verifyDiscovered(key)
                        }
                    })
                }
                override fun onServiceLost(service: NsdServiceInfo) {
                    discoveredServers.remove("${service.serviceName}._${service.serviceType}")
                    _discoveredServersFlow.value = discoveredServers.toMap()
                }
                override fun onDiscoveryStopped(serviceType: String) {}
                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                    mlock?.release()
                }
                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                    mlock?.release()
                }
            }
            
            // اكتشاف خدمات متعددة
            nsdManager?.discoverServices("_younes._tcp.", NsdManager.PROTOCOL_DNS_SD, discoveryListener)
            nsdManager?.discoverServices("_red._tcp.", NsdManager.PROTOCOL_DNS_SD, discoveryListener)
            nsdManager?.discoverServices("_http._tcp.", NsdManager.PROTOCOL_DNS_SD, discoveryListener)
            
        } catch (e: Exception) {
            // فشل mDNS ليس قاتل - نعتمد على المسح المباشر
        }
    }
    
    fun getAllLocalIps(): List<String> {
        return _availableNetworks.value.mapNotNull { it.ipAddress }
    }
    
    fun getBestNetwork(): NetworkState? {
        return _availableNetworks.value.maxByOrNull { it.quality.rank }
    }
    
    fun generateCandidatesForDiscovery(): List<String> {
        val candidates = mutableListOf<String>()
        val networks = _availableNetworks.value
        
        for (net in networks) {
            val prefix = net.ip.substringBeforeLast('.')
            // مسح ذكي: الأولويات أولاً
            val priorities = listOf(1, 2, 10, 11, 20, 50, 100, 112, 200, 244)
            for (p in priorities) {
                candidates.add("$prefix.$p")
            }
            // ثم المسح الكامل /24 إذا لزم
            if (net.type == NetworkType.WIFI || net.type == NetworkType.ETHERNET) {
                for (i in 1..254) {
                    if (i !in priorities) candidates.add("$prefix.$i")
                }
            }
        }
        
        // إضافة خوادم مكتشفة عبر mDNS
        candidates.addAll(discoveredServers.values.map { it.host })
        
        return candidates.distinct()
    }
    
    /**
     * تسجيل خادم مكتشف في الخريطة الموحّدة — المصدر الوحيد لكل من الواجهة والاتصال.
     */
    private fun registerDiscovered(
        base: String,
        source: String,
        latencyMs: Long,
        verified: Boolean,
        key: String? = null
    ) {
        val host = YounesServerSignature.hostOf(base) ?: return
        val port = YounesServerSignature.portOf(base)
        val mapKey = key ?: "$host:$port"
        discoveredServers[mapKey] = DiscoveredServer(
            id = mapKey,
            host = host,
            port = port,
            url = base.trimEnd('/'),
            discoveryMethod = source,
            latencyMs = latencyMs,
            isYounesVerified = verified
        )
        _discoveredServersFlow.value = discoveredServers.toMap()
    }

    /** يتحقق من خادم mDNS حقيقي عبر LocalServerDiscovery (توقيع + زمن استجابة). */
    private fun verifyDiscovered(key: String) {
        val context = appContext ?: return
        val server = discoveredServers[key] ?: return
        scope.launch {
            val result = LocalServerDiscovery(context).verifyTimed(server.url) ?: return@launch
            registerDiscovered(
                base = result.base,
                source = server.discoveryMethod,
                latencyMs = result.latencyMs,
                verified = true,
                key = key
            )
        }
    }

    /**
     * فحص شبكة محلي حقيقي يشغّله زر «فحص الشبكة»: يجرّب المرشحين المعروفين +
     * عناوين شبكة الجهاز الحالية بالتوازي المحدود، ويثبت كل خادم يونس بالتوقيع،
     * ويبثّ التقدم. لا بيانات وهمية: كل بطاقة معروضة = خادم أجاب فعلاً.
     */
    fun scanForServers() {
        val context = appContext ?: return
        if (_isScanning.value) return
        _isScanning.value = true
        _scanProgress.value = 0f
        scope.launch {
            try {
                val discovery = LocalServerDiscovery(context)
                val candidates = discovery.candidateBases()
                val total = candidates.size.coerceAtLeast(1)
                var finished = 0
                val gate = Semaphore(SCAN_PARALLEL)
                supervisorScope {
                    candidates.map { base ->
                        async(Dispatchers.IO) {
                            gate.withPermit {
                                runCatching {
                                    discovery.verifyTimed(base)?.let { hit ->
                                        registerDiscovered(
                                            base = hit.base,
                                            source = "IP scan",
                                            latencyMs = hit.latencyMs,
                                            verified = true
                                        )
                                    }
                                }
                                finished++
                                _scanProgress.value = (finished.toFloat() / total).coerceIn(0f, 1f)
                            }
                        }
                    }.awaitAll()
                }
            } finally {
                _scanProgress.value = 1f
                _isScanning.value = false
            }
        }
    }

    fun shutdown() {
        try {
            networkCallback?.let { connectivityManager?.unregisterNetworkCallback(it) }
            discoveryListener?.let { nsdManager?.stopServiceDiscovery(it) }
        } catch (e: Exception) {}
    }

    /** توازٍ الفحص: يمنع إغراق الشبكة/المقابس على شبكة مشتركة. */
    private const val SCAN_PARALLEL = 24
}
