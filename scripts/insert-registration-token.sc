//> using scala "3.7.2"
//> using dep "org.postgresql:postgresql:42.7.8"

import java.sql.*
import scala.util.Using
import java.util.UUID

val url      = "jdbc:postgresql://localhost:5432/happyfarm"
val user     = "happy"
val password = "farm"

val token = UUID.randomUUID().toString

println(s"Generated token: $token")

val sql =
  """
    |INSERT INTO happyfarm.registration_tokens (token)
    |VALUES (?)
    |""".stripMargin

val result =
  Using.Manager { use =>
    val conn = use(DriverManager.getConnection(url, user, password))
    val ps   = use(conn.prepareStatement(sql))
    ps.setString(1, token)
    ps.executeUpdate()
  }

result match
  case scala.util.Success(_) =>
    println(s"Successfully inserted token into registration_tokens")
  case scala.util.Failure(e) =>
    println(s"Insert failed: ${e.getMessage}")