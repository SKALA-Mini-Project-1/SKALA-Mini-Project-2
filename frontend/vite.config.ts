import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

const userAuthTarget = process.env.VITE_USER_AUTH_API_URL ?? 'http://localhost:18081'
const concertTarget = process.env.VITE_CONCERT_API_URL ?? 'http://localhost:18082'
const queueTarget = process.env.VITE_QUEUE_API_URL ?? 'http://localhost:18083'
const ticketingTarget = process.env.VITE_TICKETING_API_URL ?? 'http://localhost:18084'
const paymentTarget = process.env.VITE_PAYMENT_API_URL ?? 'http://localhost:18085'
const incidentApiTarget = process.env.VITE_INCIDENT_API_URL ?? 'http://localhost:18088'

export default defineConfig({
  plugins: [vue()],
  server: {
    allowedHosts: true,
    proxy: {
      '/api/users': {
        target: userAuthTarget,
        changeOrigin: true
      },
      '/api/concerts': {
        target: concertTarget,
        changeOrigin: true
      },
      '/api/ticketing': {
        target: queueTarget,
        changeOrigin: true
      },
      '/api/seats': {
        target: ticketingTarget,
        changeOrigin: true
      },
      '/api/bookings': {
        target: ticketingTarget,
        changeOrigin: true
      },
      '/api/payments': {
        target: paymentTarget,
        changeOrigin: true
      },
      '/ops': {
        target: incidentApiTarget,
        changeOrigin: true
      }
    }
  }
});
