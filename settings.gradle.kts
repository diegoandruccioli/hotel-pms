rootProject.name = "hotel-pms"

val projects = listOf(
    "config-service",
    "frontdesk-service",
    "guest-service",
    "billing-service",
    "fb-service",
    "auth-service",
    "api-gateway"
)
for (p in projects) {
    if (file(p).exists()) {
        include(p)
    }
}
