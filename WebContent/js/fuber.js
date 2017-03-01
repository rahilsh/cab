/**
 * 
 */
(function() {
	bookCab=function(){
		var pink= document.getElementById("pink").value;
		var data = {};
		data.pink=pink;
		data.user = JSON.stringify({
			number : '9999',
			name : 'user',
			lat : '17.414478',
			lon: '78.466646'
		});
		$.ajax({
			url : '/FRoot/api/book',
			data : data
		}).done(function(data) {
			$("#cabForm").hide();
			document.getElementById("demo").innerHTML = data.result;
			getCabs();
		});
	};
	
	makeTable = function(mydata) {
		var table = $('<table border=1 id="tableId">');
		var tblHeader = "<tr>";
		for ( var k in mydata[0])
			tblHeader += "<th>" + k + "</th>";
		tblHeader += "</tr>";
		$(tblHeader).appendTo(table);
		$.each(mydata, function(index, value) {
			var TableRow = "<tr id=" + value + ">";
			$.each(value, function(key, val) {
				TableRow += "<td>" + val + "</td>";
			});

			TableRow += "</tr>";
			$(table).append(TableRow);
		});
		return ($(table));
	};
	getCabs=function(){
		$.ajax({
			url : '/FRoot/api/cabs'
		}).done(function(data) {
			if( $.isArray(data) && data.length >0 ) {
				var tableDiv = makeTable(data);
				$("#tableId").remove();
				$(tableDiv).appendTo("#target_div");
				/*var table = document.getElementById("tableId");
				var rows = table.getElementsByTagName("tr");
				for (i = 0; i < rows.length; i++) {
					var currentRow = table.rows[i];
					var createClickHandler = function(row) {
						return function() {
							var cell = row.getElementsByTagName("td")[1];
							var id = cell.innerHTML;
							var r = confirm("Book Cab No: " + id);
							if (r == true) {
								var data = {};
								data.number = id;
								data.user = JSON.stringify({
									number : '9999',
									name : 'user',
									source : '10.10'
								});
								$.ajax({
									type: "POST",
									url : '/FRoot/api/book',
									data : data
								}).done(function(data) {
								    document.getElementById("demo").innerHTML = "Booked ="+data.result;
								});
							}
						};
					};
					currentRow.onclick = createClickHandler(currentRow);
				}*/
			}else{
				document.getElementById("demo").innerHTML = "No cabs available !!";
			}
		});
	};
	$( document ).ready(function() {
		 getCabs();
		 $("#cabForm").on("submit", function(event) {
				bookCab();
				return false;
		 });
	});
}());