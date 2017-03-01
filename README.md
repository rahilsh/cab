# cab

This project was tested on tomcat 8.0 server. Welcome page displays list of available cabs and provides a button to book nearest cab.

Description of APIs

/cab/api/cabs - To get the list of available cabs
	Parameter - none

/cab/api/book - To book a cab
	Parameter - 1. pink=on for booking pink cab
				2. user JSON for eg user = { number : '9999', name : 'user', lat : '17.414478', lon: '78.466646'});

/cab/api/stop - To stop ride
	Parameter - 1. number (Cab number)
				2. duration(Ride Durationin minutes)
				3. type(Cab type eg. pink)
				4. lat(of destination)
				5. lon(of destination)
