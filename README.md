## 🥬 Happy Farm Messenger

<p align="center">
  <img src="./demo/chatoverview.png" width="40%" />
  <img src="./demo/profile.png" width="40%" />
</p>
<p align="center">
  <img src="./demo/friends.png" width="40%" />
  <img src="./demo/chat.png" width="40%" />
</p>
<p align="center">
  <img src="./demo/userNotFound.png" width="40%" />
</p>

### [ZIO](https://zio.dev/)/[Laminar](https://laminar.dev/)/Actor powered messaging platform  🖊️

Built for fun and total data ownership, Happy Farm is a private social tool for family and friends. Under the hood, it’s a Scala powerhouse: a ZIO-native core with an in-house Actor implementation with the elegance of Laminar (Scala.js)

I have extensive experience working with Akka, but I’ve always wanted to build something non-trivial using ZIO. The effect system was a lot of fun to work with - the waterfall-style composition and explicit error handling make failures predictable and easier to reason about.

A messaging platform is a natural fit for the actor model, where deterministic message ordering within each chat room is important. I initially considered using open-source actor libraries like Pekko (since Akka is no longer fully open source) or Shardcake, but they required more plumbing than I wanted for this project. In the end, I decided to build a lightweight actor system myself, including an internal broadcasting mechanism tailored to the application.

(And Yes! I made those clickable buttons with [Pixilart](https://www.pixilart.com/))

## Features
- Token-Based Registration – Invite-only account creation via manually generated registration tokens (Scala CLI), preventing open public sign-ups
- Friend Management – Search for and add friends
- Real-Time Messaging – Text chat powered by ZIO and an Actor-based backend, with delivery status, retry on failure, and unread indicators etc
- Fault Isolation – Per-room Actors ensure failures in one chat room don’t affect others
- Typing Indicators – Real-time user typing signals
- E2E Encryption - Messages are encrypted in both transmission and storage

## Work In Progress
- Image sharing
- Video sharing
- Friendship deletion

## Demo

### Chatting &  Broadcasting real-time messages
<img src="./demo/Chatting.gif" width="75%"></img>
<img src="./demo/ChatOverview.gif" width="75%"></img>

### Add Friend 
<img src="./demo/AddFriend.gif" width="75%"></img>

### Network glitches handling
<img src="./demo/NetworkIssue.gif" width="50%"></img>

### SignIn/Out flow
<img src="./demo/SignInOutFlow.gif" width="50%"></img>

### Encryption/Decryption For Messages In Different Languages
<img src="./demo/languages.png" width="50%"></img>

## Development

1. **Prerequisite**
   - Make sure the following are installed
     - Java 21
     - Scala 3.7.2 (repo is compiled and tested against 3.7.2 - I started the project when 3.7.2 was released but feel free to upgrade to latest version and try out)
     - sbt 
     - Flyway 
     - scala-cli 
     - PostgreSQL (running locally on port 5432)
   - Recommended: Use SDKMAN
     ```shell
        sdk install java 21.0.7-amzn
        sdk install scala 3.7.2
        sdk install sbt
     ```
   - Install additional dep
     ```shell
        brew install flyway
        brew install Virtuslab/scala-cli/scala-cli
     ```
2. **Database Setup**
   - Start PostgreSQL locally
   - Connect to your Postgres instance via `psql -h localhost -p 5432 -U postgres` as superuser and run the commands in `db-set.sql`. This will create the required database and user.
   - From the project root directory, run:
     ```shell
        flyway migrate
       ```
     This applies all database migrations.
   - Handy commands:
     - `\l` - List all databases
     - `\c <db_name>` - Connect to a different database
     - `\dn` - List Schemas
     - `\dt happyfarm.*` - List Tables in `happyfarm` schema
     - `\d happyfarm.<table_name>` - Describe a table (Shows columns, types, and indices)
     - `\q` - Quit the terminal.
     - `psql "postgresql://user:password@host:port/database"` - Connecting to remotely hosted Postgres
3. **Generate a Registration Token**
   - The application uses invite-only registration
   - Navigate to the `scripts` directory and run:
     ```shell
     scala-cli insert-registration-token.sc
     ```
     This will insert a registration token into the database.
     You’ll need this token to register a user.
4. Run the Application(From the project root)
   - Build the application via
     ```shell
      sbt dev
     ```
     Or if you want smaller JS bundle(for prod)
     ```shell
      sbt prod
     ``` 
     It will build JS/CSS bundle ready be to served
   - Now run the application directly:
     ```shell
     sbt "backend / runMain com.happyfarm.backend.HappyFarmMain"
     ```
5. Access the Application
   - Open your browser and go to `localhost:8000`
   - Register user with the token generated earlier

6. Run it via Docker
   - Build via `sbt releaseDocker`
   - Run the application directly
     ```shell
     docker run -p 8080:8080 -e PGHOST="host.docker.internal" happy-farm-messenger:<version> # version from build.sbt
     ```
     - I have my Postgres running directly on my machine, so I need the application to talk to it from inside a container.

7. Publishing
    - `sbt releaseDockerRailway` builds a `linux/amd64` Docker image for deployment. It is named after Railway because I use Railway for personal hosting, but the image can be used more generally on compatible platforms
    - `sbt push` pushes the built image to GitHub Packages for Railway or other compatible platforms to pull and deploy
8. KEK Generation
    - `openssl rand -base64 32` It prints a Base64 string representing 32 random bytes if you want an env variable based KEK
      - If you want to verify the length of the generated KEK is 32 bytes, run the following in a Python3 REPL
      - ```
        import base64, os
        s = "<GENERATED_BASE64_FROM_OPENSSL>"
        print(len(base64.b64decode(s)))
        ```
    - Or Extend `EncryptionService` and implement your own KEK rotation

## Architecture

![Architecture Diagram](./docs/architecture.svg)