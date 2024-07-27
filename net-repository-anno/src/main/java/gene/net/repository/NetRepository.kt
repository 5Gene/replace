package gene.net.repository

import retrofit2.Retrofit

@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
annotation class NetSource(val method: String = "POST", val path: String, val list: Boolean = false, val extra: String = "")

var retrofitProvider: (String) -> Retrofit = { throw RuntimeException("must set retrofitProvider = { yorRetrofit } in application") }