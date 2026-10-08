// iKoco kod düzenleyicisinin CodeMirror 6 motoru: FiddleEditor.scala'nın Ace'ten kullandığı
// yüzeyi tek bir cepheye indirir (window.KocoMotor). Sözleşme: kojojs-editor#75.
// Scala tarafı yalnız buradaki adları çağırır; CodeMirror'ın kendi API'si bu dosyanın dışına çıkmaz.
import {EditorView, keymap, lineNumbers, drawSelection, highlightActiveLine, Decoration, ViewPlugin, MatchDecorator} from "@codemirror/view";
import {EditorState} from "@codemirror/state";
import {defaultKeymap, history, historyKeymap, indentWithTab, undo} from "@codemirror/commands";
import {StreamLanguage, syntaxHighlighting, defaultHighlightStyle, bracketMatching, indentOnInput, indentUnit} from "@codemirror/language";
import {scala} from "@codemirror/legacy-modes/mode/clike";
import {autocompletion, closeBrackets, closeBracketsKeymap, completionKeymap, startCompletion} from "@codemirror/autocomplete";
import {setDiagnostics, lintGutter} from "@codemirror/lint";
import TR from "./tr-anahtar.json";

// Türkçe anahtar sözcükler: Scala akış modu onları ad sanıyor; üstüne dekorasyon.
// Liste mode-scala.js'tekiyle aynı olmalı (anahtar-denetle.cjs CI'da karşılaştırıyor).
const sözcük = "[\\p{L}\\p{N}_$]";
const trEş = new MatchDecorator({
  regexp: new RegExp("(?<!" + sözcük + ")(" + TR.join("|") + ")(?!" + sözcük + ")", "gu"),
  decoration: Decoration.mark({class: "tr-anahtar"})
});
const trAnahtar = ViewPlugin.fromClass(class {
  constructor(v) { this.d = trEş.createDeco(v); }
  update(u) { this.d = trEş.updateDeco(u, this.d); }
}, {decorations: v => v.d});

// Ace'in insertMatch'i (FiddleEditor.scala) ile aynı: imzadan parametre adlarını çıkar.
// "(n: Int, renk: Renk): Birim" -> "(n, renk)"; parantezsiz imza -> "".
function parametreler(imza) {
  const parçalar = imza.split(/[()]/);
  if (parçalar.length > 2 && parçalar[0].length === 0) {
    return "(" + parçalar[1].split(",").map(p => p.split(":")[0].trim()).join(", ") + ")";
  }
  return "";
}

// Tamamlama süzgeci Türkçe'ye göre: CodeMirror'ın öntanımlısı "I"yı "i" sayıyor (ölçüldü),
// Türkçe'de "I" -> "ı". toLocaleLowerCase("tr") ikisini doğru indiriyor.
function trKüçült(s) { return s.toLocaleLowerCase("tr"); }

const tema = EditorView.theme({
  "&": {height: "100%", fontSize: "16px", backgroundColor: "#ffffff", color: "#000000"},
  ".cm-scroller": {fontFamily: "Monaco, Menlo, 'Ubuntu Mono', Consolas, source-code-pro, monospace", overflow: "auto"},
  ".cm-content": {caretColor: "#000000"},
  ".cm-gutters": {backgroundColor: "#f3f3f3", color: "#888888", border: "none"},
  ".cm-activeLineGutter": {backgroundColor: "#e8f2fe"},
  ".cm-activeLine": {backgroundColor: "#e8f2fe55"},
  ".tr-anahtar": {color: "#7f0055", fontWeight: "bold"}
});

export function ac(el) {
  let girdiCb = null;
  let tamamlayıcı = null;

  // Sunucu adaylarını Scala'dan alan tamamlama kaynağı; süzgeç ve ekleme burada.
  const kaynak = (ctx) => {
    if (!tamamlayıcı) return null;
    const söz = ctx.matchBefore(new RegExp(sözcük + "*", "u"));
    if (!söz) return null;
    if (söz.from === söz.to && !ctx.explicit) return null;
    const satır = ctx.state.doc.lineAt(ctx.pos);
    const önek = söz.text;
    return new Promise((çöz) => {
      tamamlayıcı(satır.number - 1, ctx.pos - satır.from, önek, (adaylar) => {
        const ö = trKüçült(önek);
        const seçenekler = [];
        for (const a of adaylar || []) {
          const ad = String(a.value), imza = String(a.name || "");
          if (!trKüçült(ad).startsWith(ö)) continue;
          const par = parametreler(imza);
          seçenekler.push({
            label: ad, detail: imza, type: "function",
            apply: (view, _c, from, to) => {
              const metin = ad + par;
              const aç = metin.indexOf("(");
              let anchor, head;
              if (aç === -1) { anchor = head = from + metin.length; }
              else if (metin[aç + 1] === ")") { anchor = head = from + aç + 2; }
              else {
                // ilk parametre adı seçili (Ace: selectWordRight)
                const ilk = metin.slice(aç + 1).match(new RegExp("^" + sözcük + "*", "u"))[0];
                anchor = from + aç + 1; head = anchor + ilk.length;
              }
              view.dispatch({changes: {from, to, insert: metin}, selection: {anchor, head}, userEvent: "input.complete"});
            }
          });
        }
        çöz(seçenekler.length ? {from: söz.from, options: seçenekler, filter: false} : null);
      });
    });
  };

  // defaultKeymap'in "Mod-Enter" (Windows/Linux'ta Ctrl-Enter, macOS'ta Cmd-Enter) girdisi
  // insertBlankLine'a bağlı. FiddleEditor.scala aynı tuşu GLOBAL olarak (Mousetrap.bindGlobal)
  // zaten Derle'ye bağlıyor; süzülmeden ikisi de tetikleniyordu -- bir Windows/Linux sınayıcı
  // ölçtü: Ctrl+Enter hem betiği çalıştırıyor hem de boş satır ekliyordu. Mod-Enter'ın ne
  // yapacağına kapsayan (host) karar verir; motor karışmaz.
  const temelTuşlar = defaultKeymap.filter((b) => b.key !== "Mod-Enter");
  const uzantılar = [
    lineNumbers(), lintGutter(), history(), drawSelection(), highlightActiveLine(),
    indentUnit.of("  "), indentOnInput(), bracketMatching(), closeBrackets(), EditorView.lineWrapping,
    StreamLanguage.define(scala), trAnahtar, syntaxHighlighting(defaultHighlightStyle),
    autocompletion({override: [kaynak], activateOnTyping: true}),
    EditorView.updateListener.of((u) => { if (u.docChanged && girdiCb) girdiCb(); }),
    tema,
    keymap.of([...closeBracketsKeymap, ...temelTuşlar, ...historyKeymap, ...completionKeymap, indentWithTab])
  ];
  const view = new EditorView({parent: el, state: EditorState.create({doc: "", extensions: uzantılar})});

  function konum(row, column) {
    const doc = view.state.doc;
    const satır = doc.line(Math.max(1, Math.min(doc.lines, row + 1)));
    return Math.min(satır.from + Math.max(0, column), satır.to);
  }

  return {
    getValue: () => view.state.doc.toString(),
    // Ace'in session.setValue'su gibi: geri alma geçmişi sıfırlanır (şablon açma/kapama, betik yükleme).
    setValue: (metin) => { view.setState(EditorState.create({doc: String(metin), extensions: uzantılar})); },
    // Çevir için: tek işlem, tek Ctrl+Z (docs/ace-geri-al-olcumu.js'nin C yolunun karşılığı).
    yazGeriAlinabilir: (metin) => {
      view.dispatch({changes: {from: 0, to: view.state.doc.length, insert: String(metin)}, selection: {anchor: 0}, scrollIntoView: true, userEvent: "input.cevir"});
    },
    onInput: (cb) => { girdiCb = cb; },
    focus: () => view.focus(),
    getCursorPosition: () => {
      const baş = view.state.selection.main.head, satır = view.state.doc.lineAt(baş);
      return {row: satır.number - 1, column: baş - satır.from};
    },
    moveCursorTo: (row, column) => { const p = konum(row, column); view.dispatch({selection: {anchor: p}, scrollIntoView: true}); },
    // Ace: [{row, col, text, type}] (0 tabanlı). Tanı aralığı: sütundan satır sonuna, en az bir karakter.
    setAnnotations: (liste) => {
      const doc = view.state.doc;
      const tanılar = (liste || []).map((a) => {
        const satır = doc.line(Math.max(1, Math.min(doc.lines, (a.row | 0) + 1)));
        const from = Math.min(satır.from + Math.max(0, a.col | 0), satır.to);
        const to = from < satır.to ? satır.to : Math.min(from + 1, doc.length);
        const tür = a.type === "error" ? "error" : a.type === "warning" ? "warning" : "info";
        return {from, to: Math.max(from, to), severity: tür, message: String(a.text || "")};
      });
      view.dispatch(setDiagnostics(view.state, tanılar));
    },
    clearAnnotations: () => { view.dispatch(setDiagnostics(view.state, [])); },
    // Scala: motor.setCompleter((row, col, önek, geriÇağır) => sunucudan adayları al; geriÇağır([{name, value}]))
    setCompleter: (f) => { tamamlayıcı = f; },
    complete: () => startCompletion(view),
    undo: () => undo(view),
    // sınamalar ve hata ayıklama için; Scala bunu kullanmaz
    _view: view
  };
}
