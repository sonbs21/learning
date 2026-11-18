// Reusable quiz widget.
// Markup: <div class="quiz" data-correct="1"><p class="q">...</p>
//           <button>A</button><button>B</button><button>C</button>
//           <p class="explain">shown after answering</p></div>
// data-correct is the 0-based index of the right button.
(function () {
  const css = `
  .quiz { margin: 1.4rem 0; padding: 1rem; border: 1px solid var(--rule); border-radius: 6px; background: var(--card); }
  .quiz .q { margin: 0 0 .7rem; font-weight: 600; }
  .quiz button { display: block; width: 100%; text-align: left; margin: .35rem 0; padding: .55rem .8rem;
    font: inherit; font-size: .95rem; color: var(--fg); background: var(--bg);
    border: 1px solid var(--rule); border-radius: 4px; cursor: pointer; }
  .quiz button:hover:not(:disabled) { border-color: var(--accent); }
  .quiz button:focus-visible { outline: 2px solid var(--accent); outline-offset: 2px; }
  .quiz button.right { border-color: var(--ok); box-shadow: inset 4px 0 0 var(--ok); }
  .quiz button.wrong { border-color: var(--bad); box-shadow: inset 4px 0 0 var(--bad); }
  .quiz .explain { display: none; margin: .7rem 0 0; font-size: .9rem; }
  .quiz.done .explain { display: block; }
  .quiz .verdict { font-family: system-ui, sans-serif; font-weight: 600; margin-right: .3rem; }
  .quiz-score { font-family: system-ui, sans-serif; font-size: .9rem; color: var(--muted); }`;
  const style = document.createElement('style');
  style.textContent = css;
  document.head.appendChild(style);

  const quizzes = [...document.querySelectorAll('.quiz')];
  const scoreEl = document.querySelector('.quiz-score');
  let answered = 0, correct = 0;

  quizzes.forEach((quiz) => {
    const right = Number(quiz.dataset.correct);
    const buttons = [...quiz.querySelectorAll('button')];
    const explain = quiz.querySelector('.explain');
    explain?.setAttribute('aria-live', 'polite');
    buttons.forEach((btn, i) => {
      btn.type = 'button';
      btn.addEventListener('click', () => {
        if (quiz.classList.contains('done')) return;
        quiz.classList.add('done');
        buttons.forEach((b) => (b.disabled = true));
        buttons[right].classList.add('right');
        const ok = i === right;
        if (!ok) btn.classList.add('wrong');
        if (explain) {
          const v = document.createElement('span');
          v.className = 'verdict';
          v.textContent = ok ? '✓ Đúng.' : '✗ Chưa đúng.';
          explain.prepend(v);
        }
        answered++; if (ok) correct++;
        if (scoreEl) scoreEl.textContent = `Điểm: ${correct}/${quizzes.length}` + (answered === quizzes.length ? ' · xong!' : '');
      });
    });
  });
})();
