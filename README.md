# cab

### Prerequisite

* Basic understanding of Java, Spring framework, Maven & REST APIs
* To run this app java 21 & maven is mandatory

### Reference Documentation

For further reference, please consider the following sections:

* [Official Apache Maven documentation](https://maven.apache.org/guides/index.html)
* [Spring Boot Maven Plugin Reference Guide](https://docs.spring.io/spring-boot/docs/2.2.4.RELEASE/maven-plugin/)
* [Spring Web](https://docs.spring.io/spring-boot/docs/2.2.4.RELEASE/reference/htmlsingle/#boot-features-developing-web-applications)

### What's Needed for Production

This codebase is currently a functional prototype. The following items are required to make it production-ready:

#### Critical
1. **Authentication & Authorization** - Implement JWT/OAuth2 with role-based access control for riders, drivers, and admins
2. **Production Database** - Replace H2 with PostgreSQL/MySQL and add Flyway/Liquibase for migrations
3. **API Documentation** - Add Swagger/OpenAPI documentation for all endpoints

#### Important Features
4. **User Management** - Add registration, login, and profile management for riders and drivers
5. **Booking Lifecycle** - Implement explicit cancel and complete endpoints for bookings
6. **Payment Integration** - Integrate payment gateway (Stripe, Razorpay, etc.)
7. **Real-time Updates** - Add WebSocket support for live driver location and booking status updates
8. **Notifications** - Implement SMS (Twilio) and email notifications for booking events
9. **Logging & Monitoring** - Add structured logging (ELK stack), metrics (Prometheus), and alerting
10. **Rate Limiting** - Implement API rate limiting to prevent abuse

#### Infrastructure
11. **Containerization** - Add Dockerfile and docker-compose.yml
12. **Environment Configuration** - Add profile-based config (dev/staging/prod) with externalized settings
13. **CI/CD Pipelines** - Enhance GitHub Actions with deployment to cloud (AWS/GCP/Azure)
14. **Input Sanitization** - Add comprehensive input validation and sanitization
15. **Error Handling** - Implement global exception handling with proper HTTP status codes and error responses
