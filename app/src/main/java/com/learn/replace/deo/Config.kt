package com.learn.replace.deo

import gene.net.repository.NetSource

@NetSource(path = "api/test/config", list = true)
data class Config(val name: String, val json: String)
