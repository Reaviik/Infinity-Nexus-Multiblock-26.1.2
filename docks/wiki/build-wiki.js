#!/usr/bin/env node
'use strict';

/*
 * Gera docks/wiki/index.html a partir de docks/wiki/README.md.
 *
 * Uso:
 *   node docks/wiki/build-wiki.js          # escreve index.html
 *   node docks/wiki/build-wiki.js --check  # falha se index.html estiver desatualizado
 *
 * O parser cobre o subconjunto de Markdown usado no README: titulos, paragrafos,
 * listas (com checkbox), tabelas com alinhamento, blocos de codigo, blockquotes,
 * code spans, negrito e links.
 */

const fs = require('fs');
const path = require('path');

const WIKI_DIR = __dirname;
const README_PATH = path.join(WIKI_DIR, 'README.md');
const OUTPUT_PATH = path.join(WIKI_DIR, 'index.html');
const CHECK_ONLY = process.argv.includes('--check');

function escapeHtml(value) {
  return String(value)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;');
}

function escapeAttr(value) {
  return escapeHtml(value).replace(/"/g, '&quot;');
}

function slugify(text) {
  return String(text)
    .toLowerCase()
    .replace(/[^\p{L}\p{N}_\s-]/gu, '')
    .trim()
    .replace(/\s+/g, '-')
    .replace(/-+/g, '-');
}

function plainText(markdown) {
  return String(markdown)
    .replace(/`([^`]*)`/g, '$1')
    .replace(/\*\*([^*]*)\*\*/g, '$1')
    .replace(/\[([^\]]*)\]\(([^)]*)\)/g, '$1')
    .replace(/<[^>]+>/g, '')
    .trim();
}

function renderInline(text) {
  let out = '';
  let i = 0;
  while (i < text.length) {
    const ch = text[i];

    if (ch === '`') {
      const end = text.indexOf('`', i + 1);
      if (end === -1) {
        out += escapeHtml(text.slice(i));
        break;
      }
      out += '<code>' + escapeHtml(text.slice(i + 1, end)) + '</code>';
      i = end + 1;
      continue;
    }

    if (ch === '*' && text[i + 1] === '*') {
      const end = text.indexOf('**', i + 2);
      if (end === -1) {
        out += escapeHtml(text.slice(i));
        break;
      }
      out += '<strong>' + renderInline(text.slice(i + 2, end)) + '</strong>';
      i = end + 2;
      continue;
    }

    if (ch === '[') {
      const close = text.indexOf(']', i + 1);
      if (close !== -1 && text[close + 1] === '(') {
        const paren = text.indexOf(')', close + 2);
        if (paren !== -1) {
          const label = text.slice(i + 1, close);
          const url = text.slice(close + 2, paren);
          out += '<a href="' + escapeAttr(url) + '">' + renderInline(label) + '</a>';
          i = paren + 1;
          continue;
        }
      }
    }

    let j = i;
    while (j < text.length) {
      if (text[j] === '`') break;
      if (text[j] === '*' && text[j + 1] === '*') break;
      if (text[j] === '[') break;
      j++;
    }

    if (j === i) {
      out += escapeHtml(text[i]);
      i++;
    } else {
      out += escapeHtml(text.slice(i, j));
      i = j;
    }
  }
  return out;
}

function splitRow(line) {
  let value = line.trim();
  if (value.startsWith('|')) {
    value = value.slice(1);
  }
  if (value.endsWith('|')) {
    value = value.slice(0, -1);
  }
  return value.split('|').map((cell) => cell.trim());
}

function renderMarkdown(source) {
  const lines = source.replace(/\r\n?/g, '\n').split('\n');
  const headings = [];
  const html = [];
  let i = 0;

  const isBlank = (line) => /^\s*$/.test(line);
  const headingMatch = (line) => /^(#{1,6})\s+(.*?)\s*$/.exec(line);
  const isFence = (line) => /^```/.test(line);
  const isListStart = (line) => /^\s*(?:[-*+]|\d+[.)])\s+/.test(line);
  const isBlockquote = (line) => /^\s*>/.test(line);
  const isRule = (line) => /^\s*---+\s*$/.test(line);

  function isTableStart(index) {
    if (index + 1 >= lines.length) {
      return false;
    }
    const header = lines[index];
    if (!header.includes('|')) {
      return false;
    }
    const separator = lines[index + 1];
    if (!separator.includes('|') && !/^\s*\|?[-: ]+\|?\s*$/.test(separator)) {
      return false;
    }
    const cells = splitRow(separator);
    return cells.length > 0 && cells.every((cell) => /^:?-{1,}:?$/.test(cell.trim()));
  }

  while (i < lines.length) {
    const line = lines[i];

    if (isBlank(line)) {
      i++;
      continue;
    }

    if (isFence(line)) {
      const language = line.slice(3).trim();
      i++;
      const buffer = [];
      while (i < lines.length && !/^```\s*$/.test(lines[i])) {
        buffer.push(lines[i]);
        i++;
      }
      if (i < lines.length) {
        i++;
      }
      const classAttr = language ? ' class="language-' + escapeAttr(language) + '"' : '';
      html.push('<pre><code' + classAttr + '>' + escapeHtml(buffer.join('\n')) + '\n</code></pre>');
      continue;
    }

    const heading = headingMatch(line);
    if (heading) {
      const level = heading[1].length;
      const text = heading[2];
      const label = plainText(text);
      const id = slugify(label);
      headings.push({ level, id, label });
      html.push(
        '<h' + level + ' id="' + escapeAttr(id) + '">' +
        '<a class="heading-anchor" href="#' + escapeAttr(id) + '" aria-label="Link para ' + escapeAttr(label) + '">#</a>' +
        renderInline(text) +
        '</h' + level + '>'
      );
      i++;
      continue;
    }

    if (isRule(line)) {
      html.push('<hr>');
      i++;
      continue;
    }

    if (isBlockquote(line)) {
      const buffer = [];
      while (i < lines.length && isBlockquote(lines[i])) {
        buffer.push(lines[i].replace(/^\s*>\s?/, ''));
        i++;
      }
      html.push('<blockquote>');
      html.push('<p>' + renderInline(buffer.join(' ')) + '</p>');
      html.push('</blockquote>');
      continue;
    }

    if (isTableStart(i)) {
      const headerCells = splitRow(lines[i]);
      const separatorCells = splitRow(lines[i + 1]);
      const aligns = separatorCells.map((cell) => {
        const trimmed = cell.trim();
        const left = trimmed.startsWith(':');
        const right = trimmed.endsWith(':');
        if (left && right) {
          return 'center';
        }
        if (right) {
          return 'right';
        }
        if (left) {
          return 'left';
        }
        return '';
      });
      i += 2;
      const bodyRows = [];
      while (i < lines.length && !isBlank(lines[i]) && lines[i].includes('|')) {
        bodyRows.push(splitRow(lines[i]));
        i++;
      }
      html.push('<div class="table-wrap"><table>');
      html.push('<thead>');
      html.push('<tr>' + headerCells.map((cell) => '<th>' + renderInline(cell) + '</th>').join('') + '</tr>');
      html.push('</thead>');
      html.push('<tbody>');
      for (const row of bodyRows) {
        const cells = row.map((cell, index) => {
          const align = aligns[index] ? ' align="' + aligns[index] + '"' : '';
          return '<td' + align + '>' + renderInline(cell) + '</td>';
        });
        html.push('<tr>' + cells.join('') + '</tr>');
      }
      html.push('</tbody></table></div>');
      continue;
    }

    if (isListStart(line)) {
      const ordered = /^\s*\d+[.)]\s+/.test(line);
      const items = [];
      let current = null;

      while (i < lines.length) {
        const candidate = lines[i];
        const itemMatch = /^(\s*)([-*+]|\d+[.)])\s+(.*)$/.exec(candidate);
        if (itemMatch) {
          const itemOrdered = /^\d+[.)]$/.test(itemMatch[2]);
          if (itemOrdered !== ordered) {
            break;
          }
          if (current) {
            items.push(current);
          }
          current = itemMatch[3];
          i++;
          continue;
        }

        if (isBlank(candidate)) {
          break;
        }

        const isContinuation = /^\s{2,}\S/.test(candidate) &&
          !isFence(candidate) &&
          !headingMatch(candidate) &&
          !isBlockquote(candidate) &&
          !isTableStart(i);

        if (isContinuation) {
          if (current !== null) {
            current += ' ' + candidate.trim();
          }
          i++;
          continue;
        }

        break;
      }
      if (current !== null) {
        items.push(current);
      }

      const tag = ordered ? 'ol' : 'ul';
      html.push('<' + tag + '>');
      for (const item of items) {
        const task = /^\[([ xX])\]\s+(.*)$/.exec(item);
        if (task) {
          const checked = task[1].toLowerCase() === 'x';
          html.push('<li><input disabled="" type="checkbox"' + (checked ? ' checked=""' : '') + '> ' + renderInline(task[2]) + '</li>');
        } else {
          html.push('<li>' + renderInline(item) + '</li>');
        }
      }
      html.push('</' + tag + '>');
      continue;
    }

    const paragraph = [];
    while (i < lines.length) {
      const candidate = lines[i];
      if (isBlank(candidate) ||
          isFence(candidate) ||
          headingMatch(candidate) ||
          isListStart(candidate) ||
          isBlockquote(candidate) ||
          isRule(candidate) ||
          isTableStart(i)) {
        break;
      }
      paragraph.push(candidate.trim());
      i++;
    }

    if (paragraph.length === 0) {
      paragraph.push(line.trim());
      i++;
    }
    html.push('<p>' + renderInline(paragraph.join(' ')) + '</p>');
  }

  return { html: html.join('\n'), headings };
}

function renderToc(headings) {
  return headings
    .filter((heading) => heading.level === 2 || heading.level === 3)
    .map((heading) =>
      '          <li class="toc-item toc-level-' + heading.level + '"><a href="#' + escapeAttr(heading.id) + '">' +
      escapeHtml(heading.label) + '</a></li>'
    )
    .join('\n');
}

function formatDate(date) {
  try {
    return new Intl.DateTimeFormat('pt-BR', { day: '2-digit', month: 'short', year: 'numeric' }).format(date);
  } catch (error) {
    return date.toISOString().slice(0, 10);
  }
}

function renderPage(contentHtml, tocHtml) {
  const updated = formatDate(new Date());
  return [
    '<!DOCTYPE html>',
    '<html lang="pt-BR">',
    '<head>',
    '  <meta charset="utf-8">',
    '  <meta name="viewport" content="width=device-width, initial-scale=1">',
    '  <meta name="description" content="Wiki do desenvolvedor do Infinity Nexus Multiblock: definicoes, patterns, slots, capabilities, GUI, controllers, receitas, boosters, motions e limitacoes.">',
    '  <title>Wiki do Desenvolvedor | Infinity Nexus Multiblock</title>',
    '  <link rel="stylesheet" href="wiki.css">',
    '</head>',
    '<body>',
    '  <a class="skip-link" href="#conteudo">Pular para o conteudo</a>',
    '',
    '  <header class="topbar">',
    '    <a class="brand" href="../builder/index.html" aria-label="Voltar ao Infinity Nexus Multiblock Builder">',
    '      <span class="brand-mark" aria-hidden="true">',
    '        <svg viewBox="0 0 48 48" role="img">',
    '          <path d="M24 3 44 14v20L24 45 4 34V14Z" fill="#0d1a12" stroke="#6bd64a" stroke-width="2"></path>',
    '          <path d="M24 3 44 14 24 25 4 14Z" fill="#6bd64a" opacity="0.92"></path>',
    '          <path d="M4 14 24 25v20L4 34Z" fill="#2d7a24"></path>',
    '          <path d="M44 14 24 25v20l20-11Z" fill="#4aa937"></path>',
    '          <path d="M16 20 24 24l8-4" fill="none" stroke="#0d1a12" stroke-width="2" stroke-linecap="round"></path>',
    '        </svg>',
    '      </span>',
    '      <span class="brand-copy">',
    '        <strong>Infinity Nexus</strong>',
    '        <small>Multiblock Wiki</small>',
    '      </span>',
    '    </a>',
    '',
    '    <nav class="topbar-actions" aria-label="Links da wiki">',
    '      <a class="source-link" href="addons.html">Guia de addons</a>',
    '      <a class="source-link" href="README.md">README.md</a>',
    '      <a class="btn" href="../builder/index.html">Voltar ao builder</a>',
    '    </nav>',
    '  </header>',
    '',
    '  <div class="wiki-shell">',
    '    <aside class="wiki-sidebar" aria-label="Indice da wiki">',
    '      <div class="sidebar-head">',
    '        <span class="eyebrow">Documentacao</span>',
    '        <strong>Guia completo</strong>',
    '        <small>Atualizado em ' + escapeHtml(updated) + '</small>',
    '      </div>',
    '      <label class="toc-filter">',
    '        <span class="sr-only">Filtrar secoes</span>',
    '        <input id="tocFilter" type="search" placeholder="Filtrar secoes..." autocomplete="off">',
    '      </label>',
    '      <nav class="toc" aria-label="Secoes da wiki">',
    '        <ul class="toc-list">',
    tocHtml,
    '        </ul>',
    '      </nav>',
    '    </aside>',
    '',
    '    <main id="conteudo" class="wiki-content">',
    '      <div class="doc-meta">',
    '        <span class="doc-tag">Guia do desenvolvedor</span>',
    '        <span>Minecraft 26.1.2</span>',
    '        <span>NeoForge 26.1.2.121</span>',
    '        <span>schema_version 2</span>',
    '      </div>',
    '      <article class="markdown">',
    contentHtml,
    '',
    '      </article>',
    '    </main>',
    '  </div>',
    '',
    '  <footer class="page-footer">',
    '    <span>Infinity Nexus Multiblock</span>',
    '    <a href="../builder/index.html">Voltar ao builder</a>',
    '  </footer>',
    '',
    '  <script>',
    '    (function () {',
    '      var filter = document.getElementById("tocFilter");',
    '      var items = Array.prototype.slice.call(document.querySelectorAll(".toc-item"));',
    '',
    '      if (filter) {',
    '        filter.addEventListener("input", function () {',
    '          var query = filter.value.trim().toLocaleLowerCase("pt-BR");',
    '          items.forEach(function (item) {',
    '            item.hidden = query.length > 0 && !item.textContent.toLocaleLowerCase("pt-BR").includes(query);',
    '          });',
    '        });',
    '      }',
    '',
    '      if ("IntersectionObserver" in window) {',
    '        var links = new Map();',
    '        document.querySelectorAll(".toc a").forEach(function (link) {',
    '          links.set(decodeURIComponent(link.getAttribute("href").slice(1)), link);',
    '        });',
    '',
    '        var active = null;',
    '        var observer = new IntersectionObserver(function (entries) {',
    '          var visible = entries',
    '            .filter(function (entry) { return entry.isIntersecting; })',
    '            .sort(function (a, b) { return a.boundingClientRect.top - b.boundingClientRect.top; });',
    '',
    '          if (!visible.length) {',
    '            return;',
    '          }',
    '',
    '          var next = links.get(visible[0].target.id);',
    '          if (!next || next === active) {',
    '            return;',
    '          }',
    '',
    '          if (active) {',
    '            active.classList.remove("is-active");',
    '          }',
    '          active = next;',
    '          active.classList.add("is-active");',
    '        }, { rootMargin: "-12% 0px -72% 0px", threshold: 0 });',
    '',
    '        document.querySelectorAll(".markdown h2, .markdown h3").forEach(function (heading) {',
    '          observer.observe(heading);',
    '        });',
    '      }',
    '    }());',
    '  </script>',
    '</body>',
    '</html>',
    ''
  ].join('\n');
}

function build() {
  const markdown = fs.readFileSync(README_PATH, 'utf8');
  const { html, headings } = renderMarkdown(markdown);
  const tocHtml = renderToc(headings);
  return renderPage(html, tocHtml);
}

function main() {
  const output = build();

  if (CHECK_ONLY) {
    const current = fs.existsSync(OUTPUT_PATH) ? fs.readFileSync(OUTPUT_PATH, 'utf8') : '';
    if (current !== output) {
      console.error('index.html esta desatualizado em relacao ao README.md. Rode: node docks/wiki/build-wiki.js');
      process.exit(1);
    }
    console.log('index.html esta atualizado.');
    return;
  }

  fs.writeFileSync(OUTPUT_PATH, output, 'utf8');
  console.log('Gerado ' + path.relative(process.cwd(), OUTPUT_PATH) + '.');
}

main();
