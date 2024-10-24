package replace

import groovy.json.JsonOutput
import java.io.File

object CacheAble {

    /**
     * 缓存对象,不要传入字符串
     */
    fun cache(key: String, value: Any) {
        cache(key, JsonOutput.toJson(value))
    }

    fun cache(key: String, value: String) {
        val cache = File(localRepoDirectory, ".$key")
        if (!cache.exists()) {
            cache.parentFile.mkdirs()
            log("cache file: $cache >> cache $key -> $value")
            cache.createNewFile()
        }
        cache.writeText(value)
        log("cache $key -> $value")
    }

    fun readCache(key: String, def: String = ""): String {
        val cache = File(localRepoDirectory, ".$key")
        if (cache.exists()) {
            val readText = cache.readText()
            log("read cache $key -> $readText")
            return readText
        }
        log("read cache $key -> default: $def")
        return def
    }
}