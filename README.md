## Introduction

This plugin aims to improve the compilation speed during daily development. For projects with around 30 modules, the compilation time can be very long, sometimes
taking several minutes. Even incremental compilation can take about a minute. The goal of this plugin is to shorten the incremental compilation time to just a few
seconds.

The principle behind this is to separate concerns. For the modules you are not focusing on, their source code does not need to participate in the compilation;
instead, it can be converted into localmaven dependencies. This way, only the modules you are focusing on will participate in the compilation each time you modify or
update the code, significantly reducing the compilation time.

## Usage

Introduce the plugin in your project's settings.gradle.kts:

  ```kotlin
  plugins {
    id("io.github.5hmlA.replace")
}
  ```

Configure the modules you are focusing on to participate in the compilation as source code:

  ```kotlin
  replace {
    srcProject(
        ":feature:home"
    )
}
  ```

## To-Do

Provide a task to republish modules to localmaven based on the code updated from Git.