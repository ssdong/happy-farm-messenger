package com.happyfarm.frontend.utils

import com.happyfarm.frontend.pages.ChatPage.UIMessage
import shared.model.Message

import scala.scalajs.js

object Utils:
  def fetchDateAndTime(message: UIMessage): Option[(String, String)] =
    message.maybeCreatedAt.map { createdAt =>
      val date = new js.Date(createdAt)
      // the basic js.Date facade only exposes the no-argument version of the method.
      // using js.Dynamic cuz we know this method exists in the browser,
      // even if the static type definition doesn't show it
      val newDynamicDate = date
        .asInstanceOf[js.Dynamic]

      (
        newDynamicDate
          .toLocaleDateString("en-CA") // en-CA locale formats the date to YYYY-MM-DD format
          .asInstanceOf[String],
        newDynamicDate
          .toLocaleTimeString("en-CA") // en-CA locale formats the time to hh:mm:ss a.m. or hh:mm:ss p.m.
          .asInstanceOf[String]
      )
    }
  def fetchDateAndTime(maybeMessage: Option[Message]): Option[(String, String)] =
    maybeMessage.map { message =>
      val date = new js.Date(message.createdAt)
      // the basic js.Date facade only exposes the no-argument version of the method.
      // using js.Dynamic cuz we know this method exists in the browser,
      // even if the static type definition doesn't show it
      val newDynamicDate = date
        .asInstanceOf[js.Dynamic]

      (
        newDynamicDate
          .toLocaleDateString("en-CA") // en-CA locale formats the date to YYYY-MM-DD format
          .asInstanceOf[String],
        newDynamicDate
          .toLocaleTimeString("en-CA") // en-CA locale formats the time to hh:mm:ss a.m. or hh:mm:ss p.m.
          .asInstanceOf[String]
      )
    }
