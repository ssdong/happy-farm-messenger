package com.happyfarm.frontend.pages

import com.raquo.laminar.nodes.ReactiveHtmlElement
import org.scalajs.dom

trait Page:
  def run: ReactiveHtmlElement[dom.HTMLDivElement]

object Page:
  val Ignore: Unit = ()
