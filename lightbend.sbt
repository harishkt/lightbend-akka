resolvers in ThisBuild += "lightbend-commercial-mvn" at
        "https://repo.lightbend.com/pass/orfDX6eRufgs1Rot9QKcjYzn6Cz9YgHbB9Mi4-HShCwKXIU4/commercial-releases"
resolvers in ThisBuild += Resolver.url("lightbend-commercial-ivy",
        url("https://repo.lightbend.com/pass/orfDX6eRufgs1Rot9QKcjYzn6Cz9YgHbB9Mi4-HShCwKXIU4/commercial-releases"))(Resolver.ivyStylePatterns)
