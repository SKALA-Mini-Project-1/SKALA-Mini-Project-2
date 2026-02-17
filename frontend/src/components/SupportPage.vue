<script setup lang="ts">
const faqs: Array<{ question: string; answer: string }> = [
  {
    question: '결제가 실패했는데 좌석이 사라졌어요.',
    answer:
      '결제 실패 시 좌석은 자동으로 해제될 수 있습니다. 좌석 선택 화면으로 돌아가 다시 선택 후 결제를 진행해주세요.',
  },
  {
    question: '예매 취소는 어디서 하나요?',
    answer: '마이페이지 > 최근 예매 내역에서 취소 가능한 건에 대해 예매취소를 진행할 수 있습니다.',
  },
  {
    question: '모바일 티켓은 어디서 확인하나요?',
    answer: '마이페이지에서 해당 예매 건의 모바일 티켓 버튼을 누르면 QR 티켓을 확인할 수 있습니다.',
  },
]
</script>

<template>
  <div class="mx-auto max-w-[1000px] p-3 sm:p-4 md:p-8">
    <h2 class="mb-6 border-b-2 border-[#333] pb-2 text-2xl font-bold text-[#333]">고객센터</h2>

    <section class="mb-6 rounded-xl border border-[#dfe9f5] bg-white p-5 md:p-6">
      <h3 class="text-lg font-extrabold text-[#133256]">문의 채널</h3>
      <div class="mt-4 grid gap-3 text-sm md:grid-cols-3">
        <div class="rounded-lg bg-[#f7fbff] p-4">
          <p class="font-bold text-[#173a61]">대표번호</p>
          <p class="mt-1 text-[#2f4b67]">1544-0000</p>
          <p class="mt-1 text-xs text-[#607d99]">평일 09:00~18:00 (주말/공휴일 제외)</p>
        </div>
        <div class="rounded-lg bg-[#f7fbff] p-4">
          <p class="font-bold text-[#173a61]">이메일</p>
          <p class="mt-1 text-[#2f4b67]">fairlineTicket@gmail.com</p>
          <p class="mt-1 text-xs text-[#607d99]">접수 후 영업일 기준 24시간 내 순차 답변</p>
        </div>
        <div class="rounded-lg bg-[#f7fbff] p-4">
          <p class="font-bold text-[#173a61]">운영사/주소</p>
          <p class="mt-1 text-[#2f4b67]">Fairline Ticket</p>
          <p class="mt-1 text-xs text-[#607d99]">
            경기 성남시 분당구 성남대로 343번길 9, 8층 SKALA
          </p>
        </div>
      </div>
    </section>

    <section class="rounded-xl border border-[#dfe9f5] bg-white p-5 md:p-6">
      <h3 class="text-lg font-extrabold text-[#133256]">자주 묻는 질문</h3>
      <div class="mt-4 space-y-3">
        <article
          v-for="faq in faqs"
          :key="faq.question"
          class="rounded-lg border border-[#e5edf7] bg-[#fbfdff] p-4"
        >
          <p class="text-sm font-bold text-[#1a3f66]">Q. {{ faq.question }}</p>
          <p class="mt-2 text-sm leading-relaxed text-[#3f5d7b]">A. {{ faq.answer }}</p>
        </article>
      </div>
    </section>
  </div>
</template>
