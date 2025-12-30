package com.v2ray.ang

object AppConfig {
    const val ANG_PACKAGE = BuildConfig.APPLICATION_ID
    const val TAG = BuildConfig.APPLICATION_ID
    const val DIR_ASSETS = "assets"
    const val DIR_BACKUPS = "backups"
    const val ANG_CONFIG = "ang_config"

    const val PREF_SNIFFING_ENABLED = "pref_sniffing_enabled"
    const val PREF_ROUTE_ONLY_ENABLED = "pref_route_only_enabled"
    const val PREF_PER_APP_PROXY = "pref_per_app_proxy"
    const val PREF_PER_APP_PROXY_SET = "pref_per_app_proxy_set"
    const val PREF_BYPASS_APPS = "pref_bypass_apps"
    const val PREF_LOCAL_DNS_ENABLED = "pref_local_dns_enabled"
    const val PREF_FAKE_DNS_ENABLED = "pref_fake_dns_enabled"
    const val PREF_APPEND_HTTP_PROXY = "pref_append_http_proxy"
    const val PREF_LOCAL_DNS_PORT = "pref_local_dns_port"
    const val PREF_VPN_DNS = "pref_vpn_dns"
    const val PREF_VPN_BYPASS_LAN = "pref_vpn_bypass_lan"
    const val PREF_VPN_INTERFACE_ADDRESS_CONFIG_INDEX = "pref_vpn_interface_address_config_index"
    const val PREF_SPEED_ENABLED = "pref_speed_enabled"
    const val PREF_CONFIRM_STOP = "pref_confirm_stop"
    const val PREF_START_ON_BOOT = "pref_start_on_boot"
    const val PREF_RECONNECT_WHEN_NETWORK_CHANGED = "pref_reconnect_when_network_changed"
    const val PREF_PROXY_SHARING = "pref_proxy_sharing"
    const val PREF_LOCAL_DNS_SERVER = "pref_local_dns_server"
    const val PREF_REMOTE_DNS_SERVER = "pref_remote_dns_server"
    const val PREF_DOMESTIC_DNS_SERVER = "pref_domestic_dns_server"
    const val PREF_DNS_STRATEGY = "pref_dns_strategy"
    const val PREF_ROUTING_DOMAIN_STRATEGY = "pref_routing_domain_strategy"
    const val PREF_ROUTING_MODE = "pref_routing_mode"
    const val PREF_V2RAY_NODE_STRATEGY = "pref_v2ray_node_strategy"
    const val PREF_MODE = "pref_mode"
    const val PREF_PREFER_IPV6 = "pref_prefer_ipv6"
    const val PREF_USE_HEV_TUNNEL = "pref_use_hev_tunnel"
    const val PREF_HEV_TUN_LOG_LEVEL = "pref_hev_tun_log_level"
    const val PREF_HEV_TUN_RW_TIMEOUT = "pref_hev_tun_rw_timeout"
    const val PREF_MUX_XUDP_CONCURRENCY = "pref_mux_xudp_concurrency"
    const val PREF_MUX_XUDP_QUIC = "pref_mux_xudp_quic"
    const val PREF_FRAGMENT_ENABLED = "pref_fragment_enabled"
    const val PREF_FRAGMENT_PACKETS = "pref_fragment_packets"
    const val PREF_FRAGMENT_LENGTH = "pref_fragment_length"
    const val PREF_FRAGMENT_INTERVAL = "pref_fragment_interval"
    const val PREF_UI_MODE_NIGHT = "pref_ui_mode_night"
    const val PREF_MUX_CONCURRENCY = "pref_mux_concurrency" // این متغیری بود که در ارور آخر نال شده بود

    // موارد اضافه شده برای اتو-آپدیت ساب‌سکریپشن
    const val PREF_SUB_AUTO_UPDATE = "pref_sub_auto_update"
    const val PREF_SUB_UPDATE_INTERVAL = "pref_sub_update_interval"
    const val SUBSCRIPTION_UPDATE_TASK_NAME = "subscription_update_task"
    const val SUBSCRIPTION_UPDATE_CHANNEL = "subscription_update_channel"
    const val SUBSCRIPTION_UPDATE_CHANNEL_NAME = "Subscription Update"
    const val SUBSCRIPTION_DEFAULT_UPDATE_INTERVAL = "60"

    const val HTTP_PORT = "http_port"
    const val SOCKS_PORT = "socks_port"
    const val BROADCAST_ACTION_SERVICE = "com.v2ray.ang.action.service"
    const val BROADCAST_ACTION_ACTIVITY = "com.v2ray.ang.action.activity"

    const val MSG_REGISTER_CLIENT = 1
    const val MSG_UNREGISTER_CLIENT = 2
    const val MSG_STATE_START = 3
    const val MSG_STATE_START_SUCCESS = 4
    const val MSG_STATE_START_FAILURE = 5
    const val MSG_STATE_STOP = 6
    const val MSG_STATE_STOP_SUCCESS = 7
    const val MSG_STATE_RUNNING = 8
    const val MSG_STATE_NOT_RUNNING = 9
    const val MSG_STATE_RESTART = 10
    const val MSG_MEASURE_DELAY = 11
    const val MSG_MEASURE_DELAY_SUCCESS = 12

    const val VPN = "VPN"
    const val PROXY_ONLY = "PROXY_ONLY"
    const val RAY_NG_CHANNEL_ID = "v2rayng_channel_6"
    const val RAY_NG_CHANNEL_NAME = "v2rayNG notification channel"
    const val UPLINK = "uplink"
    const val DOWNLINK = "downlink"
    const val TAG_DIRECT = "direct"
    const val TAG_BLOCKED = "blocked"
    const val LOOPBACK = "127.0.0.1"
    const val APP_WIKI_MODE = "https://github.com/2dust/v2rayNG/wiki/Mode"
    const val APP_PROMOTION_URL = "aHR0cHM6Ly90Lm1lL3YycmF5TkdfUHJvbW90aW9u"

    val DNS_GOOGLE_ADDRESSES = arrayListOf("8.8.8.8", "8.8.4.4", "2001:4860:4860::8888", "2001:4860:4860::8844")
    val DNS_QUAD9_ADDRESSES = arrayListOf("9.9.9.9", "149.112.112.112", "2620:fe::fe", "2620:fe::9")
    val DNS_YANDEX_ADDRESSES = arrayListOf("77.88.8.8", "77.88.8.1", "2a02:6b8::feed:0ff", "2a02:6b8:0:1::feed:0ff")

    val ROUTED_IP_LIST = arrayListOf(
        "0.0.0.0/5", "8.0.0.0/7", "11.0.0.0/8", "12.0.0.0/6", "16.0.0.0/4", "32.0.0.0/3",
        "64.0.0.0/2", "128.0.0.0/3", "160.0.0.0/5", "168.0.0.0/6", "172.0.0.0/12",
        "172.32.0.0/11", "172.64.0.0/10", "172.128.0.0/9", "173.0.0.0/8", "174.0.0.0/7",
        "176.0.0.0/4", "192.0.0.0/9", "192.128.0.0/11", "192.160.0.0/13", "192.169.0.0/16",
        "192.170.0.0/15", "192.172.0.0/14", "192.176.0.0/12", "192.192.0.0/10", "193.0.0.0/8",
        "194.0.0.0/7", "196.0.0.0/6", "200.0.0.0/5", "208.0.0.0/4"
    )
}
