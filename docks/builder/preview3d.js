(function (root) {
  "use strict";

  var HALF_WIDTH = 13;
  var HALF_HEIGHT = 6.5;
  var CUBE_HEIGHT = 18;
  var FALLBACK_COLOR = "#57c7ff";
  var ANCHOR_COLOR = "#8df06c";

  function parseHex(value) {
    var hex = typeof value === "string" ? value.trim() : "";
    if (/^#[0-9a-fA-F]{3}$/.test(hex)) {
      hex = "#" + hex.charAt(1) + hex.charAt(1) +
        hex.charAt(2) + hex.charAt(2) +
        hex.charAt(3) + hex.charAt(3);
    }
    if (!/^#[0-9a-fA-F]{6}$/.test(hex)) {
      hex = FALLBACK_COLOR;
    }
    var number = parseInt(hex.slice(1), 16);
    return {
      r: (number >> 16) & 255,
      g: (number >> 8) & 255,
      b: number & 255
    };
  }

  function shade(value, factor) {
    var color = parseHex(value);
    function channel(part) {
      return Math.max(0, Math.min(255, Math.round(part * factor)));
    }
    return "rgb(" + channel(color.r) + ", " + channel(color.g) + ", " + channel(color.b) + ")";
  }

  function project(x, y, z) {
    return {
      x: (x - z) * HALF_WIDTH,
      y: (x + z) * HALF_HEIGHT - y * CUBE_HEIGHT
    };
  }

  function collectCells(layers, colors, fallback) {
    var source = Array.isArray(layers) ? layers : [];
    var cells = [];
    source.forEach(function (layer, y) {
      if (!Array.isArray(layer)) {
        return;
      }
      layer.forEach(function (row, z) {
        if (typeof row !== "string") {
          return;
        }
        for (var x = 0; x < row.length; x += 1) {
          var symbol = row.charAt(x);
          if (symbol === " ") {
            continue;
          }
          cells.push({
            x: x,
            y: y,
            z: z,
            symbol: symbol,
            color: colors && colors[symbol] ? colors[symbol] : fallback
          });
        }
      });
    });
    return cells;
  }

  function drawFace(context, points, fill, stroke, lineWidth) {
    context.beginPath();
    context.moveTo(points[0].x, points[0].y);
    for (var index = 1; index < points.length; index += 1) {
      context.lineTo(points[index].x, points[index].y);
    }
    context.closePath();
    context.fillStyle = fill;
    context.fill();
    context.strokeStyle = stroke;
    context.lineWidth = lineWidth;
    context.stroke();
  }

  function topDiamond(point) {
    return [
      { x: point.x, y: point.y - HALF_HEIGHT },
      { x: point.x + HALF_WIDTH, y: point.y },
      { x: point.x, y: point.y + HALF_HEIGHT },
      { x: point.x - HALF_WIDTH, y: point.y }
    ];
  }

  function draw(canvas, layers, anchor, activeLayer, colors, fallback) {
    if (!canvas) {
      return;
    }
    var rect = canvas.getBoundingClientRect();
    var cssWidth = rect.width;
    var cssHeight = rect.height;
    if (cssWidth < 1 || cssHeight < 1) {
      return;
    }

    var ratio = Math.min(root.devicePixelRatio || 1, 2);
    var pixelWidth = Math.max(1, Math.round(cssWidth * ratio));
    var pixelHeight = Math.max(1, Math.round(cssHeight * ratio));
    if (canvas.width !== pixelWidth || canvas.height !== pixelHeight) {
      canvas.width = pixelWidth;
      canvas.height = pixelHeight;
    }

    var context = canvas.getContext("2d");
    if (!context) {
      return;
    }
    context.setTransform(ratio, 0, 0, ratio, 0, 0);
    context.clearRect(0, 0, cssWidth, cssHeight);

    var cells = collectCells(layers, colors, fallback || FALLBACK_COLOR);
    if (cells.length === 0) {
      context.fillStyle = "rgba(142, 163, 173, 0.75)";
      context.font = "12px 'Segoe UI', sans-serif";
      context.textAlign = "center";
      context.textBaseline = "middle";
      context.fillText("Pinte uma célula para ver a prévia.", cssWidth / 2, cssHeight / 2);
      return;
    }

    var minX = Infinity;
    var maxX = -Infinity;
    var minY = Infinity;
    var maxY = -Infinity;
    cells.forEach(function (cell) {
      var point = project(cell.x, cell.y, cell.z);
      minX = Math.min(minX, point.x - HALF_WIDTH);
      maxX = Math.max(maxX, point.x + HALF_WIDTH);
      minY = Math.min(minY, point.y - HALF_HEIGHT - CUBE_HEIGHT);
      maxY = Math.max(maxY, point.y + HALF_HEIGHT);
    });
    minX -= 6;
    maxX += 6;
    minY -= 6;
    maxY += 6;

    var contentWidth = Math.max(1, maxX - minX);
    var contentHeight = Math.max(1, maxY - minY);
    var scale = Math.min((cssWidth - 24) / contentWidth, (cssHeight - 24) / contentHeight);
    scale = Math.max(0.05, Math.min(1.9, scale));

    context.save();
    context.translate(
      cssWidth / 2 - ((minX + maxX) / 2) * scale,
      cssHeight / 2 - ((minY + maxY) / 2) * scale
    );
    context.scale(scale, scale);
    context.lineJoin = "round";

    var lineWidth = Math.max(0.35, 0.9 / scale);
    cells.sort(function (left, right) {
      var leftDepth = left.x + left.y + left.z;
      var rightDepth = right.x + right.y + right.z;
      if (leftDepth !== rightDepth) {
        return leftDepth - rightDepth;
      }
      return (left.x - left.z) - (right.x - right.z);
    });

    cells.forEach(function (cell) {
      var point = project(cell.x, cell.y, cell.z);
      var top = topDiamond(point);
      var leftBottom = { x: point.x - HALF_WIDTH, y: point.y + CUBE_HEIGHT };
      var middleBottom = { x: point.x, y: point.y + HALF_HEIGHT + CUBE_HEIGHT };
      var rightBottom = { x: point.x + HALF_WIDTH, y: point.y + CUBE_HEIGHT };
      var inactive = typeof activeLayer === "number" && cell.y !== activeLayer;

      context.globalAlpha = inactive ? 0.26 : 1;
      drawFace(context, [
        top[3],
        top[2],
        middleBottom,
        leftBottom
      ], shade(cell.color, 0.62), "rgba(0, 0, 0, 0.28)", lineWidth);
      drawFace(context, [
        top[2],
        top[1],
        rightBottom,
        middleBottom
      ], shade(cell.color, 0.84), "rgba(0, 0, 0, 0.24)", lineWidth);
      drawFace(context, top, shade(cell.color, 1.16), "rgba(255, 255, 255, 0.08)", lineWidth);
    });

    context.globalAlpha = 1;
    cells.forEach(function (cell) {
      if (cell.symbol !== anchor) {
        return;
      }
      var point = project(cell.x, cell.y, cell.z);
      var top = topDiamond(point);
      context.save();
      context.shadowColor = ANCHOR_COLOR;
      context.shadowBlur = 9 / scale;
      context.strokeStyle = ANCHOR_COLOR;
      context.lineWidth = Math.max(1.2, 2 / scale);
      context.beginPath();
      context.moveTo(top[0].x, top[0].y);
      for (var index = 1; index < top.length; index += 1) {
        context.lineTo(top[index].x, top[index].y);
      }
      context.closePath();
      context.stroke();
      context.shadowBlur = 0;
      context.fillStyle = "rgba(8, 23, 12, 0.92)";
      context.font = "700 " + (9 / scale) + "px 'Segoe UI', sans-serif";
      context.textAlign = "center";
      context.textBaseline = "middle";
      context.fillText(cell.symbol, point.x, point.y + 0.5);
      context.restore();
    });

    context.restore();
  }

  root.MultiblockPreview = {
    draw: draw
  };
})(typeof window !== "undefined" ? window : this);
