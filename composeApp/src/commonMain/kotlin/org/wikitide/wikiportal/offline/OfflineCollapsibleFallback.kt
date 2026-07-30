package org.wikitide.wikiportal.offline


const val OFFLINE_COLLAPSIBLE_FALLBACK_SCRIPT = """<script>
(function() {
  function applyFallback() {
    document.querySelectorAll('.mw-collapsible').forEach(function(box) {
      if (box.dataset.wpCollapsibleFallback) return;
      box.dataset.wpCollapsibleFallback = '1';

      var content = box.querySelector('.mw-collapsible-content');
      var rows = box.tagName === 'TABLE' ? Array.prototype.slice.call(box.rows, 1) : null;
      var collapsed = box.classList.contains('mw-collapsed');

      var toggle = box.querySelector('.mw-collapsible-toggle');
      if (!toggle) {
        toggle = document.createElement('a');
        toggle.className = 'mw-collapsible-toggle';
        toggle.href = '#';
        var host = box.tagName === 'TABLE'
          ? (box.rows[0] && box.rows[0].cells[box.rows[0].cells.length - 1])
          : box.firstElementChild;
        if (host) host.appendChild(toggle);
      }

      function render() {
        toggle.textContent = collapsed ? '[show]' : '[hide]';
        if (content) content.style.display = collapsed ? 'none' : '';
        if (rows) rows.forEach(function(row) { row.style.display = collapsed ? 'none' : ''; });
      }

      toggle.addEventListener('click', function(event) {
        event.preventDefault();
        collapsed = !collapsed;
        render();
      });

      render();
    });
  }

  if (window.jQuery && window.jQuery.fn && window.jQuery.fn.makeCollapsible) {
    window.jQuery('.mw-collapsible').makeCollapsible();
  } else {
    applyFallback();
  }
})();
</script>"""
