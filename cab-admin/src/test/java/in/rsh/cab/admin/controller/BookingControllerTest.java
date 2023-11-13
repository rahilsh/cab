package in.rsh.cab.admin.controller;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.google.gson.*;
import in.rsh.cab.admin.model.request.AddCabRequest;
import in.rsh.cab.admin.model.request.AddCityRequest;
import in.rsh.cab.admin.model.request.BookCabRequest;
import in.rsh.cab.commons.adapter.LocalDateTimeDeserializer;
import in.rsh.cab.commons.adapter.LocalDateTimeSerializer;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@AutoConfigureMockMvc
class BookingControllerTest {

  @Autowired private MockMvc mvc;
  private final Gson gson =
      new GsonBuilder()
          .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeDeserializer())
          .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeSerializer())
          .create();

  @Test
  void testBookings() throws Exception {
    onboardCities();
    final BookCabRequest bookCabRequest = onboardCab();
    bookCab(bookCabRequest);
  }

  private void bookCab(BookCabRequest bookCabRequest) throws Exception {
    mvc.perform(
            post("/bookings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(gson.toJson(bookCabRequest)))
        .andExpect(status().isOk())
        .andDo(print())
        .andExpect(jsonPath("$.bookedBy", is(String.valueOf(bookCabRequest.employeeId()))))
        .andExpect(jsonPath("$.cabId", is(String.valueOf(1))));
  }

  private BookCabRequest onboardCab() throws Exception {
    mvc.perform(
            post("/cabs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(gson.toJson(new AddCabRequest(1, 1, "HECTOR"))))
        .andExpect(status().isOk())
        .andDo(print());

    return new BookCabRequest(1, 1, 2);
  }

  private void onboardCities() throws Exception {
    mvc.perform(
            post("/cities")
                .contentType(MediaType.APPLICATION_JSON)
                .content(gson.toJson(new AddCityRequest("BLR", "KA"))))
        .andExpect(status().isOk())
        .andDo(print());

    mvc.perform(
            post("/cities")
                .contentType(MediaType.APPLICATION_JSON)
                .content(gson.toJson(new AddCityRequest("MUM", "MH"))))
        .andExpect(status().isOk())
        .andDo(print());
  }

  @Test
  void testGetAllBookings() throws Exception {
    mvc.perform(get("/bookings").contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andDo(print())
        .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
        .andExpect(jsonPath("$[0].cabId", is(String.valueOf(1))))
        .andExpect(jsonPath("$[0].bookedBy", is(String.valueOf(1))));
  }
}
