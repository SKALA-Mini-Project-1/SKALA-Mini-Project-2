import { createRouter, createWebHistory } from "vue-router";

import PaymentRedirectSuccess from "@/pages/payments/PaymentRedirectSuccess.vue";
import PaymentRedirectFail from "@/pages/payments/PaymentRedirectFail.vue";
import BookingCompletePage from "@/pages/booking/BookingCompletePage.vue";

const routes = [
  { path: "/payments/success", component: PaymentRedirectSuccess },
  { path: "/payments/fail", component: PaymentRedirectFail },
  { path: "/booking/complete", component: BookingCompletePage },
];

export default createRouter({
  history: createWebHistory(),
  routes,
});
