package shared

import zio.schema.{DeriveSchema, Schema}

case class LoginRequest(name: String, password: String)

object LoginRequest:
  given schema: Schema[LoginRequest] = DeriveSchema.gen[LoginRequest]