package in.rsh.cab.admin.controller;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.google.gson.Gson;
import in.rsh.cab.admin.model.request.AddCabRequest;
import in.rsh.cab.admin.model.request.AddCityRequest;
import in.rsh.cab.admin.model.request.BookCabRequest;
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
                .content(new Gson().toJson(bookCabRequest)))
        .andExpect(status().isOk())
        .andDo(print())
        .andExpect(jsonPath("$.employeeId", is(bookCabRequest.getEmployeeId())))
        .andExpect(jsonPath("$.cabId", is(1)));
  }

  private BookCabRequest onboardCab() throws Exception {
    mvc.perform(
            post("/cabs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(new Gson().toJson(new AddCabRequest(1, 1, "HECTOR"))))
        .andExpect(status().isOk())
        .andDo(print());

    return new BookCabRequest(1, 1, 2);
  }

  private void onboardCities() throws Exception {
    mvc.perform(
            post("/cities")
                .contentType(MediaType.APPLICATION_JSON)
                .content(new Gson().toJson(new AddCityRequest("BLR", "KA"))))
        .andExpect(status().isOk())
        .andDo(print());

    mvc.perform(
            post("/cities")
                .contentType(MediaType.APPLICATION_JSON)
                .content(new Gson().toJson(new AddCityRequest("MUM", "MH"))))
        .andExpect(status().isOk())
        .andDo(print());
  }

  @Test
  void testGetAllBookings() throws Exception {
    mvc.perform(get("/bookings").contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andDo(print())
        .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
        .andExpect(jsonPath("$[0].cabId", is(1)))
        .andExpect(jsonPath("$[0].employeeId", is(1)));
  }
}
