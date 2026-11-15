package com.restorant;

import com.restorant.dto.Api;
import com.restorant.entity.*;
import com.restorant.validation.PhoneNumbers;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Philippine mobile numbers are accepted in every common spelling and stored as +639XXXXXXXXX everywhere. */
class PhoneNumberTest extends IntegrationTestBase {
    @Test void everyAcceptedSpellingNormalizesToTheSameNumber() {
        for(String input:List.of("9171234567","09171234567","+639171234567","639171234567"," 0917 123 4567 ","+63 917-123-4567","(0917) 123.4567"))
            assertThat(PhoneNumbers.normalize(input)).as(input).isEqualTo("+639171234567");
        assertThat(PhoneNumbers.local("+639171234567")).isEqualTo("09171234567");
        assertThat(PhoneNumbers.local("9171234567")).isEqualTo("09171234567");
        assertThat(PhoneNumbers.local("landline 123")).as("unrecognised legacy values are shown unchanged").isEqualTo("landline 123");
    }
    @Test void numbersThatAreNotPhilippineMobilesAreRejected() {
        for(String input:List.of("","917123456","91712345678","8171234567","0817123456","+6391712345","+19171234567","09171234567x","0917123456a","12345abc","+63+9171234567"))
            assertThat(PhoneNumbers.isValid(input)).as(input).isFalse();
        assertThat(PhoneNumbers.isValid(null)).isFalse();
        assertThatThrownBy(()->PhoneNumbers.normalize("02 8123 4567")).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("+63");
        assertThatThrownBy(()->new Customer("Sam","sam@example.com","8171234567","$2a$10$"+"a".repeat(53))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void registrationStoresTheCanonicalNumberForEverySpelling() throws Exception {
        int n=0;
        for(String phone:List.of("9181234567","09181234567","+639181234567","0918 123 4567")) {
            var input=new Api.Register("Sam","sam"+(n++)+"@example.com",phone,password,password);
            mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json").content(json.writeValueAsString(input)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.phoneNumber").value("+639181234567"));
        }
        assertThat(users.findAll()).extracting(User::getPhoneNumber).allMatch(p->p.matches("\\+639\\d{9}"));
        for(String phone:List.of("8181234567","091812345","+63918123456789","phone")) {
            var input=new Api.Register("Sam","bad"+(n++)+"@example.com",phone,password,password);
            mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json").content(json.writeValueAsString(input)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("+63")));
        }
    }
    @Test void reservationsInquiriesAndAdminFormsUseTheSameRule() throws Exception {
        // Reservation contact number.
        var r=book(ALEX,new Api.Booking(day(),LocalTime.NOON,2,"0919 555 0001",null));
        assertThat(r.contactNumber()).isEqualTo("+639195550001");
        mvc.perform(post("/api/reservations").with(csrf()).with(user(ALEX).roles("CUSTOMER")).contentType("application/json")
            .content(json.writeValueAsString(new Api.Booking(day(),LocalTime.of(15,0),2,"5550001",null)))).andExpect(status().isBadRequest());
        // Inquiry.
        mvc.perform(post("/api/inquiries").with(csrf()).contentType("application/json")
            .content(json.writeValueAsString(new Api.InquiryInput("Visitor","visitor@example.com","9195550002","Parking?")))).andExpect(status().isCreated());
        mvc.perform(get("/api/admin/inquiries").with(user(MANAGER).roles("ADMIN"))).andExpect(jsonPath("$[0].phoneNumber").value("+639195550002"));
        mvc.perform(post("/api/inquiries").with(csrf()).contentType("application/json")
            .content(json.writeValueAsString(new Api.InquiryInput("Visitor","visitor@example.com","1234567","Parking?")))).andExpect(status().isBadRequest());
        // Admin: staff account, customer creation and customer profile edit.
        mvc.perform(post("/api/admin/accounts").with(csrf()).with(user(MANAGER).roles("ADMIN")).contentType("application/json")
            .content(json.writeValueAsString(new Api.Register("Staff","staff@example.com","639195550003",password,password))))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.phoneNumber").value("+639195550003"));
        mvc.perform(post("/api/admin/customers").with(csrf()).with(user(MANAGER).roles("ADMIN")).contentType("application/json")
            .content(json.writeValueAsString(new Api.Register("Walk-in","walkin@example.com","09195550004",password,password))))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.phoneNumber").value("+639195550004"));
        Long alex=users.findByEmail(ALEX).orElseThrow().getId();
        mvc.perform(put("/api/admin/customers/"+alex).with(csrf()).with(user(MANAGER).roles("ADMIN")).contentType("application/json")
            .content(json.writeValueAsString(new Api.Profile("Alex","9195550005",true)))).andExpect(status().isOk()).andExpect(jsonPath("$.phoneNumber").value("+639195550005"));
        mvc.perform(put("/api/admin/customers/"+alex).with(csrf()).with(user(MANAGER).roles("ADMIN")).contentType("application/json")
            .content(json.writeValueAsString(new Api.Profile("Alex","0219555000",true)))).andExpect(status().isBadRequest());
        // Staff can search by the local 09 spelling or by the stored +63 form.
        mvc.perform(get("/api/admin/customers").param("q","09195550005").with(user(MANAGER).roles("ADMIN"))).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].email").value(ALEX));
        mvc.perform(get("/api/admin/customers").param("q","+639195550004").with(user(MANAGER).roles("ADMIN"))).andExpect(jsonPath("$.length()").value(1));
    }
}
