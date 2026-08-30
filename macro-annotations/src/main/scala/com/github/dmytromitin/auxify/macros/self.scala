package com.github.dmytromitin.auxify.macros

import paradise3.api.expander

import scala.annotation.StaticAnnotation

@expander("com.github.dmytromitin.auxify.macros.internal.SelfHandler")
class self extends StaticAnnotation
