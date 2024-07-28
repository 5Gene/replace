package com.learn.media.export

//生成Mdidia模块的同名同结构的类，需要访问的模块compileOnly此模块即可变异期间可见
object Media {
    fun toast() {
        throw RuntimeException("eee")
    }
}