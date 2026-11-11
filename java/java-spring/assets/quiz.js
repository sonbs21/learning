// Reusable quiz widget.
// Usage: <div class="quiz" data-quiz="quiz-id"></div>
//        <script type="application/json" id="quiz-id">[{ "q": "...", "options": ["..."], "answer": 0, "why": "..." }]</script>
//        <script src="../assets/quiz.js"></script>
(function () {
  const css = `
  .quiz-q { border: 1px solid var(--rule); border-radius: 8px; padding: .8rem 1rem; margin: 1rem 0; }
  .quiz-q p { margin: 0 0 .6rem; }
  .quiz-q button { display: block; width: 100%; text-align: left; margin: .35rem 0; padding: .5rem .7rem;
    font: inherit; font-size: .95rem; color: var(--fg); background: var(--bg);
    border: 1px solid var(--rule); border-radius: 6px; cursor: pointer; }
  .quiz-q button:hover:not(:disabled) { border-color: var(--accent); }
  .quiz-q button.ok { border-color: var(--ok); color: var(--ok); font-weight: 600; }
  .quiz-q button.bad { border-color: var(--bad); color: var(--bad); text-decoration: line-through; }
  .quiz-why { display: none; font-size: .9rem; margin-top: .5rem; color: var(--muted); }
  .quiz-q.done .quiz-why { display: block; }
  .quiz-score { font-style: italic; color: var(--muted); }`;
  const style = document.createElement("style");
  style.textContent = css;
  document.head.appendChild(style);

  document.querySelectorAll(".quiz[data-quiz]").forEach((root) => {
    const data = JSON.parse(document.getElementById(root.dataset.quiz).textContent);
    let answered = 0, correct = 0;
    const score = document.createElement("p");
    score.className = "quiz-score";

    data.forEach((item, qi) => {
      const box = document.createElement("div");
      box.className = "quiz-q";
      const p = document.createElement("p");
      p.innerHTML = `<strong>${qi + 1}.</strong> ${item.q}`;
      box.appendChild(p);

      item.options.forEach((text, oi) => {
        const b = document.createElement("button");
        b.type = "button";
        b.textContent = text;
        b.addEventListener("click", () => {
          if (box.classList.contains("done")) return;
          box.classList.add("done");
          answered++;
          if (oi === item.answer) correct++;
          else b.classList.add("bad");
          box.querySelectorAll("button")[item.answer].classList.add("ok");
          box.querySelectorAll("button").forEach((x) => (x.disabled = true));
          score.textContent = `Đúng ${correct}/${answered} (tổng ${data.length} câu)`;
        });
        box.appendChild(b);
      });

      const why = document.createElement("div");
      why.className = "quiz-why";
      why.innerHTML = item.why;
      box.appendChild(why);
      root.appendChild(box);
    });
    root.appendChild(score);
  });
})();
