// Quiz widget. Markup:
// <div class="quiz" data-answer="1">          <- 0-based index of the correct option
//   <p class="q">Question</p>
//   <div class="opts"><button>A</button><button>B</button></div>
//   <div class="explain">Shown after answering</div>
// </div>
// <div class="quiz-score"></div>               <- optional running score
(function () {
  const quizzes = [...document.querySelectorAll(".quiz")];
  const scoreEl = document.querySelector(".quiz-score");
  let answered = 0, correct = 0;

  function renderScore() {
    if (!scoreEl) return;
    scoreEl.textContent = answered === quizzes.length
      ? `Kết quả: ${correct}/${quizzes.length} câu đúng${correct === quizzes.length ? " 🎯" : " — đọc lại phần giải thích các câu sai."}`
      : `Đã trả lời ${answered}/${quizzes.length}`;
  }

  quizzes.forEach((quiz) => {
    const answer = Number(quiz.dataset.answer);
    const buttons = [...quiz.querySelectorAll(".opts button")];
    buttons.forEach((btn, i) => {
      btn.type = "button";
      btn.addEventListener("click", () => {
        buttons.forEach((b) => (b.disabled = true));
        buttons[answer].classList.add("correct");
        if (i !== answer) btn.classList.add("wrong");
        quiz.classList.add("answered");
        answered++;
        if (i === answer) correct++;
        renderScore();
      });
    });
  });
  renderScore();
})();
