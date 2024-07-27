package com.learn.home.dto

import gene.net.repository.NetSource

@NetSource(path = "api/{userid}/home", list = true)
data class Home(val head:String,val title:String,val desc:String)

@NetSource(path = "api/{userid}/ad", list = true)
data class AD(val head:String,val title:String,val desc:String)
