package com.audioboost.pro.models

/**
 * 闊抽噺澧炲己閰嶇疆锛圧oot 鐗堬級
 *
 * 涓?NoRoot 鐗堝尯鍒細
 *  - 鏂板 systemVolumeBoostEnabled锛堢郴缁熺骇闊抽噺绐佺牬涓婇檺锛?
 *  - 鏂板 audioFlingerNodeEnabled锛堝啓 /sys/class/audio 鑺傜偣锛?
 *  - 鏂板 globalAudioPolicyEnabled锛堜慨鏀?AudioPolicy 閰嶇疆锛?
 *  - 鏂板 shizukuAudioBridgeEnabled锛坈md media_audio 妗ユ帴锛?
 *
 * 鑳藉姏鎵╁睍锛堥渶 Shizuku 鎺堟潈锛夛細
 *  - 閫氳繃 Shizuku 璋冪敤 media volume --set 璁剧疆绯荤粺闊抽噺绐佺牬涓婇檺
 *  - 閫氳繃 Shizuku 鍐?/sys/class/audio/pcm 鑺傜偣锛堥儴鍒嗚澶囨敮鎸侊級
 *  - 閫氳繃 Shizuku 淇敼 AudioPolicy 閰嶇疆
 *  - 閫氳繃 Shizuku 鎵ц cmd media_audio
 */
data class AudioConfig(
    // ===== 鍩虹锛堝悓 NoRoot锛?=====
    var packageName: String = "",
    var masterEnabled: Boolean = true,
    var volumeBoostEnabled: Boolean = true,
    var bassBoostEnabled: Boolean = false,
    var equalizerEnabled: Boolean = false,

    // ===== 瀹為獙鎬э紙鍚?NoRoot锛?=====
    var speakerBoostEnabled: Boolean = false,
    var micBoostEnabled: Boolean = false,
    var audioQualityEnhanceEnabled: Boolean = false,

    // ===== Root 涓撳睘锛堢郴缁熺骇锛?=====
    var systemVolumeBoostEnabled: Boolean = false,   // 绯荤粺绾ч煶閲忕獊鐮翠笂闄愶紙Shizuku media volume --set锛?
    var audioFlingerNodeEnabled: Boolean = false,    // 鍐?/sys/class/audio 鑺傜偣锛堥儴鍒嗚澶囨敮鎸侊級

    // ===== Root 涓撳睘瀹為獙鎬?=====
    var globalAudioPolicyEnabled: Boolean = false,   // 淇敼 AudioPolicy 閰嶇疆
    var shizukuAudioBridgeEnabled: Boolean = false,  // cmd media_audio 妗ユ帴

    // ===== 绯荤粺绾у寮猴紙Task24 鏂板锛?====
    /** AudioPolicy 閰嶇疆 Hack锛圫hizuku 鍐?Magisk overlay 鎸佷箙鍖?audio_policy_configuration.xml锛?*/
    var audioPolicyHackEnabled: Boolean = false,
    /** tinymix 闊抽娣烽煶鍣ㄦ帰娴嬶紙Shizuku 鎵ц tinymix 鑾峰彇/璁剧疆娣烽煶鍣ㄦ帶浠讹級 */
    var tinymixProbeEnabled: Boolean = false,

    // ===== 鍙傛暟 =====
    var boostLevel: Int = 150,
    var bassLevel: Int = 50,
    var eqBands: MutableList<Int> = mutableListOf(0, 0, 0, 0, 0),
    var micBoostLevel: Int = 150,
    var targetSampleRate: Int = 48000,
    var targetBitDepth: Int = 16,
    var speakerBoostMax: Int = 15,
    // Root 涓撳睘鍙傛暟
    var systemVolumeMaxBoost: Int = 50,     // 绯荤粺闊抽噺棰濆鎻愬崌鐧惧垎姣旓紙%锛?
    var pcmNodePath: String = "/sys/class/audio/pcm",  // PCM 鑺傜偣璺緞
    var audioPolicySampleRate: Int = 48000, // AudioPolicy 鐩爣閲囨牱鐜?

    var lastModified: Long = 0L
)
