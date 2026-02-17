<template>
  <div class="mx-auto max-w-[1000px] p-3 sm:p-4 md:p-8">
    <h2 class="mb-6 border-b-2 border-[#333] pb-2 text-2xl font-bold text-[#333]">예매가이드</h2>

    <section class="mb-6 rounded-xl border border-[#dfe9f5] bg-white p-5 md:p-6">
      <h3 class="text-lg font-extrabold text-[#133256]">빠른 예매 순서</h3>
      <ol class="mt-4 space-y-3 text-sm text-[#2f4b67]">
        <li class="rounded-lg bg-[#f7fbff] px-4 py-3"><span class="mr-2 font-black text-[#ff7a00]">1.</span>원하는 공연 상세 페이지에서 날짜/회차를 선택합니다.</li>
        <li class="rounded-lg bg-[#f7fbff] px-4 py-3"><span class="mr-2 font-black text-[#ff7a00]">2.</span>오픈 시간 이후 대기열 입장 후 좌석을 선택합니다.</li>
        <li class="rounded-lg bg-[#f7fbff] px-4 py-3"><span class="mr-2 font-black text-[#ff7a00]">3.</span>결제 완료 후 예매내역에서 모바일 티켓(QR)을 확인합니다.</li>
      </ol>
    </section>

    <section class="mb-6 grid gap-4 md:grid-cols-2">
      <article class="rounded-xl border border-[#dfe9f5] bg-white p-5">
        <h4 class="text-base font-extrabold text-[#133256]">좌석 선택 유의사항</h4>
        <ul class="mt-3 space-y-2 text-sm text-[#3d5a78]">
          <li>좌석 선택 화면은 제한 시간이 있어, 시간 내 결제를 완료해야 합니다.</li>
          <li>선택 가능한 좌석만 클릭되며, 이미 점유된 좌석은 선택할 수 없습니다.</li>
          <li>예매 진행 중 새로고침하면 선택 상태가 초기화될 수 있습니다.</li>
        </ul>
      </article>
      <article class="rounded-xl border border-[#dfe9f5] bg-white p-5">
        <h4 class="text-base font-extrabold text-[#133256]">결제/취소 안내</h4>
        <ul class="mt-3 space-y-2 text-sm text-[#3d5a78]">
          <li>결제 실패 시 예매는 확정되지 않으며, 다시 시도할 수 있습니다.</li>
          <li>완료된 예매는 마이페이지에서 내역 확인 및 취소가 가능합니다.</li>
          <li>취소 정책은 공연별로 상이할 수 있으니 상세 페이지를 확인해주세요.</li>
        </ul>
      </article>
    </section>

    <section class="rounded-xl border border-[#ffe5c8] bg-[#fff8ef] p-5 text-sm text-[#7d5a33]">
      <p class="font-bold text-[#8e663c]">오픈 시간 팁</p>
      <p class="mt-1">
        인기 공연은 오픈 직후 트래픽이 집중됩니다. 로그인 상태를 미리 확인하고, 결제 수단 등록을 사전에 완료하면 예매 성공률이 높아집니다.
      </p>
    </section>
  </div>
</template>
