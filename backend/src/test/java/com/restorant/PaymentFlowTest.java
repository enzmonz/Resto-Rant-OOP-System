package com.restorant;

import com.restorant.dto.Api;
import com.restorant.entity.*;
import com.restorant.exception.ApiException;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Table package pricing, the manual GCash payment states, partial cancellation and refund limits. */
class PaymentFlowTest extends IntegrationTestBase {
    private static final byte[] PNG={(byte)0x89,'P','N','G',0x0D,0x0A,0x1A,0x0A,0,0,0,0};
    /** Three tables seating 2, 4 and 6, booked together for ten guests: 600 + 1,100 + 1,600 = 3,300. */
    private Api.BookingView threeTables() {
        tables.deleteAll();
        var ids=new ArrayList<Long>();
        for(int seats:List.of(2,4,6))ids.add(tables.save(new RestaurantTable("P"+seats,seats)).getId());
        return book(ALEX,booking(LocalTime.NOON,10,120,ids));
    }
    private Long tableWith(Api.BookingView r,int seats) { return r.items().stream().filter(i->i.capacity()==seats).findFirst().orElseThrow().tableId(); }

    // ---- Pricing ----

    @Test void priceIsTheSumOfTheSelectedTablePackagesAndIsSnapshotted() throws Exception {
        var r=threeTables();
        assertThat(r.items()).extracting(Api.ItemView::price).usingElementComparator(BigDecimal::compareTo)
            .containsExactly(new BigDecimal("600"),new BigDecimal("1100"),new BigDecimal("1600"));
        assertThat(r.money().originalTotal()).isEqualByComparingTo("3300");
        assertThat(r.money().due()).isEqualByComparingTo("3300");
        assertThat(r.paymentStatus()).isEqualTo(Reservation.PaymentStatus.UNPAID);
        // An administrator raises the six-seat package; the existing reservation keeps its snapshot.
        mvc.perform(put("/api/admin/pricing/6").with(csrf()).with(user(ALEX).roles("CUSTOMER")).contentType("application/json").content("{\"price\":2000}")).andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/pricing/6").with(csrf()).with(user(MANAGER).roles("ADMIN")).contentType("application/json").content("{\"price\":2000}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.price").value(2000.00));
        mvc.perform(put("/api/admin/pricing/6").with(csrf()).with(user(MANAGER).roles("ADMIN")).contentType("application/json").content("{\"price\":-5}")).andExpect(status().isBadRequest());
        assertThat(service.detail(ALEX,r.id()).money().originalTotal()).isEqualByComparingTo("3300");
        assertThat(service.detail(ALEX,r.id()).items().getLast().price()).isEqualByComparingTo("1600");
        var later=book(JAMIE,booking(LocalTime.of(15,0),6,120,List.of(tableWith(r,6))));
        assertThat(later.money().originalTotal()).isEqualByComparingTo("2000");
        mvc.perform(get("/api/pricing")).andExpect(status().isOk()).andExpect(jsonPath("$.packages[?(@.capacity==6)].price").value(2000.00))
            .andExpect(jsonPath("$.payment.gcashNumber").value("09778021234")).andExpect(jsonPath("$.payment.automaticConfirmation").value(false));
    }
    @Test void clientSuppliedPricesNeverOverrideTheServerCalculation() throws Exception {
        String forged="{\"date\":\""+day()+"\",\"time\":\"12:00\",\"guestCount\":4,\"contactNumber\":\"9171234567\",\"policyAccepted\":true,"
            +"\"price\":1,\"totalAmount\":1,\"depositAmount\":1,\"paymentStatus\":\"PAID\",\"items\":[{\"price\":1}]%s}";
        // Extra price and status fields are ignored: the server prices the table itself and the booking is unpaid.
        mvc.perform(post("/api/reservations").with(csrf()).with(user(ALEX).roles("CUSTOMER")).contentType("application/json").content(forged.formatted("")))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.money.originalTotal").value(1100.00)).andExpect(jsonPath("$.money.due").value(1100.00))
            .andExpect(jsonPath("$.paymentStatus").value("UNPAID")).andExpect(jsonPath("$.items[0].price").value(1100.00));
        // A total that differs from the server's is rejected instead of being charged or stored.
        long before=reservations.count();
        mvc.perform(post("/api/reservations").with(csrf()).with(user(JAMIE).roles("CUSTOMER")).contentType("application/json")
            .content(forged.replace("12:00","15:00").formatted(",\"expectedTotal\":1"))).andExpect(status().isConflict());
        assertThat(reservations.count()).isEqualTo(before);
        mvc.perform(post("/api/reservations").with(csrf()).with(user(JAMIE).roles("CUSTOMER")).contentType("application/json")
            .content(forged.replace("12:00","15:00").formatted(",\"expectedTotal\":1100"))).andExpect(status().isCreated()).andExpect(jsonPath("$.money.due").value(1100.00));
    }
    @Test void aCapacityWithoutItsOwnPackageUsesTheNearestSmallerPerSeatRate() {
        tables.deleteAll();var eight=tables.save(new RestaurantTable("Long",8));
        // 1,600 for 6 seats is 266.67 per seat; 8 seats round up to a whole peso.
        assertThat(book(ALEX,booking(LocalTime.NOON,8,120,List.of(eight.getId()))).money().originalTotal()).isEqualByComparingTo("2134");
        pricing.update(8,new BigDecimal("2500"),MANAGER);
        assertThat(book(JAMIE,booking(LocalTime.of(15,0),8,120,List.of())).money().originalTotal()).isEqualByComparingTo("2500");
        assertThat(pricing.packages()).extracting(Api.PackageView::capacity).containsExactly(2,4,6,8);
    }
    @Test void aRetriedBookingRequestReturnsTheSameReservation() {
        var id=UUID.randomUUID();
        var input=new Api.Booking(day(),LocalTime.NOON,4,"09171234567",null,List.of(),List.of(),true,120,new BigDecimal("1100"),id);
        var first=book(ALEX,input);
        assertThat(book(ALEX,input).id()).isEqualTo(first.id());
        assertThat(reservations.count()).isEqualTo(1);
        assertThatThrownBy(()->book(JAMIE,input)).hasMessageContaining("already used");
    }

    // ---- Payment states ----

    @Test void aSubmittedReferenceStaysPendingUntilStaffVerifyIt() throws Exception {
        var r=book(ALEX,booking(LocalTime.NOON));
        assertThatThrownBy(()->service.changeStatus(r.id(),Reservation.Status.CONFIRMED,MANAGER)).hasMessageContaining("payment");
        mvc.perform(multipart("/api/reservations/"+r.id()+"/payments").param("reference","1234 567 890123").with(csrf()).with(user(ALEX).roles("CUSTOMER")))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.paymentStatus").value("PENDING")).andExpect(jsonPath("$.money.paid").value(0.00))
            .andExpect(jsonPath("$.payments[0].status").value("PENDING")).andExpect(jsonPath("$.payments[0].reference").value("1234567890123"))
            .andExpect(jsonPath("$.payments[0].amount").value(1100.00)).andExpect(jsonPath("$.payments[0].receivingAccount").value("09778021234"));
        // A reference number is a claim, not proof: the reservation still cannot be confirmed.
        assertThatThrownBy(()->service.changeStatus(r.id(),Reservation.Status.CONFIRMED,MANAGER)).hasMessageContaining("payment");
        assertThatThrownBy(()->submitPayment(ALEX,r.id(),"9999999999999")).hasMessageContaining("cannot be submitted");
        Long paymentId=payments.findAll().getFirst().getId();
        // Customers cannot review payments, and there is no customer endpoint that marks one paid.
        mvc.perform(put("/api/admin/payments/"+paymentId+"/review").with(csrf()).with(user(ALEX).roles("CUSTOMER")).contentType("application/json")
            .content("{\"approved\":true,\"verifiedAgainstAccount\":true}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/reservations/"+r.id()+"/demo-payment").with(csrf()).with(user(ALEX).roles("CUSTOMER"))).andExpect(status().is4xxClientError());
        mvc.perform(post("/api/reservations/demo-checkout").with(csrf()).with(user(ALEX).roles("CUSTOMER")).contentType("application/json").content("{}")).andExpect(status().is4xxClientError());
        assertThat(service.detail(ALEX,r.id()).paymentStatus()).isEqualTo(Reservation.PaymentStatus.PENDING);
        // Staff must attest that they checked the receiving account.
        mvc.perform(put("/api/admin/payments/"+paymentId+"/review").with(csrf()).with(user(MANAGER).roles("ADMIN")).contentType("application/json")
            .content("{\"approved\":true,\"verifiedAgainstAccount\":false}")).andExpect(status().isBadRequest());
        mvc.perform(put("/api/admin/payments/"+paymentId+"/review").with(csrf()).with(user(MANAGER).roles("ADMIN")).contentType("application/json")
            .content("{\"approved\":true,\"verifiedAgainstAccount\":true,\"note\":\"Seen in GCash history\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PAID")).andExpect(jsonPath("$.reviewedBy").value(MANAGER));
        var paid=service.detail(ALEX,r.id());
        assertThat(paid.paymentStatus()).isEqualTo(Reservation.PaymentStatus.PAID);
        assertThat(paid.money().paid()).isEqualByComparingTo("1100");assertThat(paid.money().due()).isEqualByComparingTo("0");
        // A decided payment cannot be decided again.
        assertThatThrownBy(()->paymentService.review(paymentId,new Api.PaymentReview(false,false,"Changed my mind"),MANAGER)).isInstanceOf(IllegalStateException.class);
        assertThat(service.changeStatus(r.id(),Reservation.Status.CONFIRMED,MANAGER).status()).isEqualTo(Reservation.Status.CONFIRMED);
        assertThatThrownBy(()->submitPayment(ALEX,r.id(),"8888888888888")).hasMessageContaining("cannot be submitted");
    }
    @Test void rejectedPaymentsCanBeResubmittedAndReferencesCannotBeReused() {
        var r=book(ALEX,booking(LocalTime.NOON));
        var other=book(JAMIE,booking(LocalTime.of(15,0)));
        assertThatThrownBy(()->submitPayment(ALEX,r.id(),"123")).hasMessageContaining("reference number");
        assertThatThrownBy(()->submitPayment(JAMIE,r.id(),"1234567890123")).hasMessageContaining("not found");
        var pending=submitPayment(ALEX,r.id(),"1234567890123");
        assertThatThrownBy(()->submitPayment(JAMIE,other.id(),"1234-567-890123")).hasMessageContaining("already submitted");
        Long first=pending.payments().getFirst().id();
        assertThatThrownBy(()->paymentService.review(first,new Api.PaymentReview(false,false," "),MANAGER)).hasMessageContaining("rejected");
        paymentService.review(first,new Api.PaymentReview(false,false,"No such transfer in the account"),MANAGER);
        var rejected=service.detail(ALEX,r.id());
        assertThat(rejected.paymentStatus()).isEqualTo(Reservation.PaymentStatus.REJECTED);
        assertThat(rejected.money().due()).isEqualByComparingTo("1100");
        assertThatThrownBy(()->service.changeStatus(r.id(),Reservation.Status.CONFIRMED,MANAGER)).hasMessageContaining("payment");
        // A rejected reference is free to be claimed correctly; the customer submits again.
        assertThat(submitPayment(JAMIE,other.id(),"1234567890123").paymentStatus()).isEqualTo(Reservation.PaymentStatus.PENDING);
        var again=submitPayment(ALEX,r.id(),"7777777777777");
        assertThat(again.payments()).extracting(Api.PaymentView::status).containsExactly(Payment.Status.REJECTED,Payment.Status.PENDING);
        assertThat(auditEntries.findByReservationIdOrderByIdAsc(r.id())).extracting(AuditEntry::getAction)
            .containsExactly("RESERVATION_CREATED","PAYMENT_SUBMITTED","PAYMENT_REJECTED","PAYMENT_SUBMITTED");
    }
    @Test void proofImagesAreValidatedStoredAndVisibleToStaffOnly() throws Exception {
        var r=book(ALEX,booking(LocalTime.NOON));
        mvc.perform(multipart("/api/reservations/"+r.id()+"/payments").file(new MockMultipartFile("proof","receipt.png","image/png","<script>alert(1)</script>".getBytes()))
            .param("reference","1234567890123").with(csrf()).with(user(ALEX).roles("CUSTOMER"))).andExpect(status().isBadRequest());
        assertThat(payments.count()).isZero();
        mvc.perform(multipart("/api/reservations/"+r.id()+"/payments").file(new MockMultipartFile("proof","receipt.png","text/html",PNG))
            .param("reference","1234567890123").with(csrf()).with(user(ALEX).roles("CUSTOMER")))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.payments[0].hasProof").value(true));
        Long paymentId=payments.findAll().getFirst().getId();
        mvc.perform(get("/api/admin/payments/"+paymentId+"/proof").with(user(ALEX).roles("CUSTOMER"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/payments/"+paymentId+"/proof").with(user(MANAGER).roles("ADMIN"))).andExpect(status().isOk())
            .andExpect(content().contentType("image/png")).andExpect(content().bytes(PNG));
        mvc.perform(get("/api/admin/payments").param("status","PENDING").with(user(MANAGER).roles("ADMIN"))).andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].customerName").value("Alex"));
    }

    // ---- Partial cancellation ----

    @Test void cancellingOneTableKeepsTheRestBookedAndMakesItsPackageRefundable() throws Exception {
        var r=threeTables();
        pay(ALEX,r.id(),"1234567890123");
        service.changeStatus(r.id(),Reservation.Status.CONFIRMED,MANAGER);
        mvc.perform(post("/api/reservations/"+r.id()+"/cancel-tables").with(csrf()).with(user(JAMIE).roles("CUSTOMER")).contentType("application/json")
            .content("{\"tableIds\":["+tableWith(r,2)+"]}")).andExpect(status().isNotFound());
        mvc.perform(post("/api/reservations/"+r.id()+"/cancel-tables").with(csrf()).with(user(ALEX).roles("CUSTOMER")).contentType("application/json")
            .content("{\"tableIds\":["+tableWith(r,2)+"]}")).andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CONFIRMED")).andExpect(jsonPath("$.tableIds.length()").value(2)).andExpect(jsonPath("$.guestCount").value(10))
            .andExpect(jsonPath("$.seats").value(10)).andExpect(jsonPath("$.money.originalTotal").value(3300.00)).andExpect(jsonPath("$.money.currentTotal").value(2700.00))
            .andExpect(jsonPath("$.money.paid").value(3300.00)).andExpect(jsonPath("$.money.refundDue").value(600.00));
        var after=service.detail(ALEX,r.id());
        assertThat(after.items()).extracting(Api.ItemView::status).containsExactly(ReservationItem.Status.CANCELLED,ReservationItem.Status.ACTIVE,ReservationItem.Status.ACTIVE);
        assertThat(after.items().getFirst().refundableAmount()).isEqualByComparingTo("600");
        assertThat(after.items().getFirst().cancelledBy()).isEqualTo(ALEX);
        assertThat(after.payments()).hasSize(1);
    }
    @Test void remainingSeatsMustFitTheGuestsOrTheGuestCountIsReducedInTheSameOperation() {
        var r=threeTables();
        pay(ALEX,r.id(),"1234567890123");
        Long six=tableWith(r,6),four=tableWith(r,4),two=tableWith(r,2);
        // Ten guests cannot stay on the six seats that would remain.
        assertThatThrownBy(()->service.cancelTables(ALEX,r.id(),new Api.CancelTables(List.of(four),null))).isInstanceOf(IllegalStateException.class).hasMessageContaining("Reduce the guest count to 8");
        assertThatThrownBy(()->service.cancelTables(ALEX,r.id(),new Api.CancelTables(List.of(four),9))).hasMessageContaining("Reduce the guest count");
        assertThatThrownBy(()->service.cancelTables(ALEX,r.id(),new Api.CancelTables(List.of(four),11))).hasMessageContaining("only be reduced");
        assertThat(service.detail(ALEX,r.id()).tableIds()).hasSize(3);
        var reduced=service.cancelTables(ALEX,r.id(),new Api.CancelTables(List.of(four),8));
        assertThat(reduced.guestCount()).isEqualTo(8);assertThat(reduced.seats()).isEqualTo(8);
        assertThat(reduced.money().refundDue()).isEqualByComparingTo("1100");
        // A second partial cancellation adds to the refundable amount; history of the first is preserved.
        var again=service.cancelTables(ALEX,r.id(),new Api.CancelTables(List.of(six),2));
        assertThat(again.guestCount()).isEqualTo(2);assertThat(again.tableIds()).containsExactly(two);
        assertThat(again.money().refundDue()).isEqualByComparingTo("2700");assertThat(again.money().originalTotal()).isEqualByComparingTo("3300");
        assertThat(again.tablesCancellable()).isFalse();
        assertThatThrownBy(()->service.cancelTables(ALEX,r.id(),new Api.CancelTables(List.of(two),null))).hasMessageContaining("At least one table must remain");
        assertThatThrownBy(()->service.cancelTables(ALEX,r.id(),new Api.CancelTables(List.of(six),null))).hasMessageContaining("still part of this reservation");
        // Cancelling what is left finishes the reservation: everything paid is now refundable.
        assertThat(service.cancel(ALEX,r.id()).money().refundDue()).isEqualByComparingTo("3300");
        assertThat(auditEntries.findByReservationIdOrderByIdAsc(r.id())).extracting(AuditEntry::getAction)
            .containsExactly("RESERVATION_CREATED","PAYMENT_SUBMITTED","PAYMENT_VERIFIED","TABLES_CANCELLED","TABLES_CANCELLED","RESERVATION_CANCELLED");
    }
    @Test void theCancellationPolicyAppliesToTablesAndWholeReservationsAlike() {
        var r=threeTables();
        pay(ALEX,r.id(),"1234567890123");
        // Ninety minutes before the visit: inside the two-hour cutoff.
        clock.setNow(day().atTime(10,30));
        assertThat(service.detail(ALEX,r.id()).cancellable()).isFalse();
        assertThatThrownBy(()->service.cancelTables(ALEX,r.id(),new Api.CancelTables(List.of(tableWith(r,2)),null))).hasMessageContaining("2 hours before");
        assertThatThrownBy(()->service.cancel(ALEX,r.id())).hasMessageContaining("2 hours before");
        // Staff may still release a table late, but under the policy it is not refundable.
        var late=service.cancelTablesAsStaff(r.id(),new Api.CancelTables(List.of(tableWith(r,2)),null),MANAGER);
        assertThat(late.items().getFirst().refundableAmount()).isEqualByComparingTo("0");
        assertThat(late.money().refundDue()).isEqualByComparingTo("0");
        assertThat(service.changeStatus(r.id(),Reservation.Status.CANCELLED,MANAGER).money().refundDue()).isEqualByComparingTo("0");
        // A minute before the cutoff a cancellation is still on time and refundable.
        clock.reset();
        var onTime=book(JAMIE,booking(LocalTime.of(15,0)));
        pay(JAMIE,onTime.id(),"5555555555555");
        clock.setNow(day().atTime(12,59));
        assertThat(service.cancel(JAMIE,onTime.id()).money().refundDue()).isEqualByComparingTo("1100");
    }
    @Test void cancellingBeforePaymentLowersTheAmountDueAndUnverifiedMoneyIsNeverRefundable() {
        var r=threeTables();
        var smaller=service.cancelTables(ALEX,r.id(),new Api.CancelTables(List.of(tableWith(r,2)),null));
        assertThat(smaller.money().due()).isEqualByComparingTo("2700");assertThat(smaller.money().refundDue()).isEqualByComparingTo("0");
        var pending=submitPayment(ALEX,r.id(),"1234567890123");
        assertThat(pending.payments().getFirst().amount()).isEqualByComparingTo("2700");
        var cancelled=service.cancel(ALEX,r.id());
        assertThat(cancelled.money().refundDue()).as("a pending claim is not money received").isEqualByComparingTo("0");
        assertThatThrownBy(()->paymentService.createRefund(r.id(),new Api.RefundInput(new BigDecimal("100"),"Customer asked"),MANAGER)).hasMessageContaining("no verified payment");
        // If staff later find the transfer, the cancelled tables that were paid for become refundable.
        paymentService.review(pending.payments().getFirst().id(),new Api.PaymentReview(true,true,null),MANAGER);
        assertThat(service.detail(ALEX,r.id()).money().refundDue()).isEqualByComparingTo("2700");
    }

    // ---- Refund limits ----

    @Test void refundsAreTrackedManuallyAndCannotBeDuplicatedOrExceedThePayment() throws Exception {
        var r=threeTables();
        pay(ALEX,r.id(),"1234567890123");
        service.cancelTables(ALEX,r.id(),new Api.CancelTables(List.of(tableWith(r,2)),null));
        String refund="{\"amount\":600,\"reason\":\"Customer cancelled the two-seat table\"}";
        mvc.perform(post("/api/admin/reservations/"+r.id()+"/refunds").with(csrf()).with(user(ALEX).roles("CUSTOMER")).contentType("application/json").content(refund)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/refunds/due").with(user(MANAGER).roles("ADMIN"))).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].money.refundDue").value(600.00));
        mvc.perform(post("/api/admin/reservations/"+r.id()+"/refunds").with(csrf()).with(user(MANAGER).roles("ADMIN")).contentType("application/json").content("{\"amount\":3300.01,\"reason\":\"Too much\"}")).andExpect(status().isConflict());
        mvc.perform(post("/api/admin/reservations/"+r.id()+"/refunds").with(csrf()).with(user(MANAGER).roles("ADMIN")).contentType("application/json").content("{\"amount\":600}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/admin/reservations/"+r.id()+"/refunds").with(csrf()).with(user(MANAGER).roles("ADMIN")).contentType("application/json").content(refund))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PENDING")).andExpect(jsonPath("$.amount").value(600.00))
            .andExpect(jsonPath("$.paymentReference").value("1234567890123")).andExpect(jsonPath("$.createdBy").value(MANAGER)).andExpect(jsonPath("$.refundReference").doesNotExist());
        Long refundId=refunds.findAll().getFirst().getId();
        // A pending refund is not money sent, but it already uses up the balance and blocks a duplicate.
        var pending=service.detail(ALEX,r.id()).money();
        assertThat(pending.refundPending()).isEqualByComparingTo("600");assertThat(pending.refunded()).isEqualByComparingTo("0");
        assertThat(pending.refundDue()).isEqualByComparingTo("0");assertThat(pending.maxRefundable()).isEqualByComparingTo("2700");
        mvc.perform(post("/api/admin/reservations/"+r.id()+"/refunds").with(csrf()).with(user(MANAGER).roles("ADMIN")).contentType("application/json").content(refund)).andExpect(status().isConflict());
        mvc.perform(get("/api/admin/refunds/due").with(user(MANAGER).roles("ADMIN"))).andExpect(jsonPath("$.length()").value(0));
        // Completing needs the reference of the refund staff sent by hand; it can be done once.
        mvc.perform(put("/api/admin/refunds/"+refundId+"/complete").with(csrf()).with(user(MANAGER).roles("ADMIN")).contentType("application/json").content("{\"refundReference\":\"\"}")).andExpect(status().isBadRequest());
        mvc.perform(put("/api/admin/refunds/"+refundId+"/complete").with(csrf()).with(user(MANAGER).roles("ADMIN")).contentType("application/json").content("{\"refundReference\":\"9000000000001\",\"note\":\"Sent from the restaurant GCash\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED")).andExpect(jsonPath("$.refundReference").value("9000000000001")).andExpect(jsonPath("$.completedBy").value(MANAGER));
        mvc.perform(put("/api/admin/refunds/"+refundId+"/complete").with(csrf()).with(user(MANAGER).roles("ADMIN")).contentType("application/json").content("{\"refundReference\":\"9000000000002\"}")).andExpect(status().isConflict());
        mvc.perform(put("/api/admin/refunds/"+refundId+"/void").with(csrf()).with(user(MANAGER).roles("ADMIN")).contentType("application/json").content("{\"note\":\"Mistake\"}")).andExpect(status().isConflict());
        assertThat(service.detail(ALEX,r.id()).money().refunded()).isEqualByComparingTo("600");
        // The ceiling is the verified payment minus everything already refunded.
        assertThatThrownBy(()->paymentService.createRefund(r.id(),new Api.RefundInput(new BigDecimal("2700.01"),"Goodwill"),MANAGER)).hasMessageContaining("exceeds");
        var goodwill=paymentService.createRefund(r.id(),new Api.RefundInput(new BigDecimal("2700"),"Kitchen closed early"),MANAGER);
        assertThat(service.detail(ALEX,r.id()).money().maxRefundable()).isEqualByComparingTo("0");
        // The same refund reference cannot be recorded twice, and a voided refund frees its amount again.
        assertThatThrownBy(()->paymentService.completeRefund(goodwill.id(),new Api.RefundCompletion("9000-0000-00001",null),MANAGER)).hasMessageContaining("already recorded");
        assertThatThrownBy(()->paymentService.voidRefund(goodwill.id()," ",MANAGER)).hasMessageContaining("voided");
        assertThat(paymentService.voidRefund(goodwill.id(),"Entered by mistake",MANAGER).status()).isEqualTo(Refund.Status.VOIDED);
        assertThat(service.detail(ALEX,r.id()).money().maxRefundable()).isEqualByComparingTo("2700");
        assertThat(service.detail(ALEX,r.id()).refunds()).extracting(Api.RefundView::status).containsExactly(Refund.Status.COMPLETED,Refund.Status.VOIDED);
        mvc.perform(get("/api/admin/reservations/"+r.id()+"/audit").with(user(MANAGER).roles("ADMIN"))).andExpect(status().isOk())
            .andExpect(jsonPath("$[*].action").value(org.hamcrest.Matchers.contains("RESERVATION_CREATED","PAYMENT_SUBMITTED","PAYMENT_VERIFIED","TABLES_CANCELLED",
                "REFUND_CREATED","REFUND_COMPLETED","REFUND_CREATED","REFUND_VOIDED")))
            .andExpect(jsonPath("$[4].actor").value(MANAGER));
        mvc.perform(get("/api/admin/reservations/"+r.id()+"/audit").with(user(ALEX).roles("CUSTOMER"))).andExpect(status().isForbidden());
    }
    @Test void staffCannotSwapTablesOnceAPaymentIsOnFile() {
        var r=book(ALEX,booking(LocalTime.NOON));
        var spare=tables.save(new RestaurantTable("T02",6));
        var moved=bookingService.edit(r.id(),booking(LocalTime.of(15,0),4,120,List.of(spare.getId())),MANAGER);
        assertThat(moved.money().due()).as("an unpaid booking is repriced for its new table").isEqualByComparingTo("1600");
        assertThat(moved.items()).extracting(Api.ItemView::status).containsExactly(ReservationItem.Status.REMOVED,ReservationItem.Status.ACTIVE);
        submitPayment(ALEX,r.id(),"1234567890123");
        Long original=r.tableIds().getFirst();
        assertThatThrownBy(()->bookingService.edit(r.id(),booking(LocalTime.of(15,0),4,120,List.of(original)),MANAGER)).hasMessageContaining("payment is on file");
        // Moving the time while keeping the paid table is still allowed.
        assertThat(bookingService.edit(r.id(),booking(LocalTime.of(17,0),4,120,List.of(spare.getId())),MANAGER).startsAt()).isEqualTo(day().atTime(17,0));
    }
}
