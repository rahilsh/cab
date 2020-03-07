# cab

This project was tested on tomcat 8.0 server. Welcome page displays list of available cabs and provides a button to book nearest cab.

Description of APIs

GET /api/cabs - To get the list of available cabs
	Parameter - none

POST /api/cabs/ - To book a cab
	Parameter - 1. pink=on for booking pink cab
				2. user JSON for eg user = { number : '9999', name : 'user', lat : '17.414478', lon: '78.466646'});

PATCH /api/cabs/{cabId} - To complete the ride
	Parameter - 1. cabId (Cab ID)
				2. duration(Ride Duration in minutes)
				3. type(Cab type eg. pink)
				4. lat(of destination)
				5. lon(of destination)

### Reference Documentation
For further reference, please consider the following sections:

* [Official Apache Maven documentation](https://maven.apache.org/guides/index.html)
* [Spring Boot Maven Plugin Reference Guide](https://docs.spring.io/spring-boot/docs/2.2.4.RELEASE/maven-plugin/)
* [Spring Web](https://docs.spring.io/spring-boot/docs/2.2.4.RELEASE/reference/htmlsingle/#boot-features-developing-web-applications)

### Guides
The following guides illustrate how to use some features concretely:

* [Building a RESTful Web Service](https://spring.io/guides/gs/rest-service/)
* [Serving Web Content with Spring MVC](https://spring.io/guides/gs/serving-web-content/)
* [Building REST services with Spring](https://spring.io/guides/tutorials/bookmarks/)

