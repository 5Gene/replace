package com.learn.profile.dto

import gene.net.repository.NetSource

@NetSource(path = "api/test/profile")
data class Profile(val name: String, val nickName: String, val avatar: String)
