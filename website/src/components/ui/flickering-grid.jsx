/*
 * Flickering Grid — ported from Magic UI, MIT.
 * Copyright (c) 2023 Dillion Verma.
 * https://github.com/magicuidesign/magicui
 *
 * Plain JSX instead of TypeScript. Two changes: for a reader who asked for less
 * motion it draws one still frame and stops, and the time step is capped, so a
 * tab coming back after a minute does not re-roll every square in one frame.
 */
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { prefersReducedMotion } from '../../hooks.js'
import { cn } from '../../lib/utils.js'

export function FlickeringGrid({
  squareSize = 4,
  gridGap = 6,
  flickerChance = 0.3,
  color = 'rgb(0, 0, 0)',
  width,
  height,
  className,
  maxOpacity = 0.3,
  ...props
}) {
  const canvasRef = useRef(null)
  const containerRef = useRef(null)
  const [isInView, setIsInView] = useState(false)
  const [canvasSize, setCanvasSize] = useState({ width: 0, height: 0 })

  // Canvas wants rgba(); let the browser parse whatever color was passed.
  const memoizedColor = useMemo(() => {
    const canvas = document.createElement('canvas')
    canvas.width = canvas.height = 1
    const ctx = canvas.getContext('2d')
    if (!ctx) return 'rgba(255, 0, 0,'
    ctx.fillStyle = color
    ctx.fillRect(0, 0, 1, 1)
    const [r, g, b] = Array.from(ctx.getImageData(0, 0, 1, 1).data)
    return `rgba(${r}, ${g}, ${b},`
  }, [color])

  const setupCanvas = useCallback(
    (canvas, w, h) => {
      const dpr = window.devicePixelRatio || 1
      canvas.width = w * dpr
      canvas.height = h * dpr
      canvas.style.width = `${w}px`
      canvas.style.height = `${h}px`
      const cols = Math.ceil(w / (squareSize + gridGap))
      const rows = Math.ceil(h / (squareSize + gridGap))

      const squares = new Float32Array(cols * rows)
      for (let i = 0; i < squares.length; i++) {
        squares[i] = Math.random() * maxOpacity
      }

      return { cols, rows, squares, dpr }
    },
    [squareSize, gridGap, maxOpacity],
  )

  const updateSquares = useCallback(
    (squares, deltaTime) => {
      for (let i = 0; i < squares.length; i++) {
        if (Math.random() < flickerChance * deltaTime) {
          squares[i] = Math.random() * maxOpacity
        }
      }
    },
    [flickerChance, maxOpacity],
  )

  const drawGrid = useCallback(
    (ctx, w, h, cols, rows, squares, dpr) => {
      ctx.clearRect(0, 0, w, h)
      for (let i = 0; i < cols; i++) {
        for (let j = 0; j < rows; j++) {
          ctx.fillStyle = `${memoizedColor}${squares[i * rows + j]})`
          ctx.fillRect(
            i * (squareSize + gridGap) * dpr,
            j * (squareSize + gridGap) * dpr,
            squareSize * dpr,
            squareSize * dpr,
          )
        }
      }
    },
    [memoizedColor, squareSize, gridGap],
  )

  useEffect(() => {
    const canvas = canvasRef.current
    const container = containerRef.current
    const ctx = canvas?.getContext('2d') ?? null
    if (!canvas || !container || !ctx) return

    const still = prefersReducedMotion()
    let frame = null
    let grid = null

    const draw = () =>
      grid && drawGrid(ctx, canvas.width, canvas.height, grid.cols, grid.rows, grid.squares, grid.dpr)

    const updateCanvasSize = () => {
      const w = width || container.clientWidth
      const h = height || container.clientHeight
      setCanvasSize({ width: w, height: h })
      grid = setupCanvas(canvas, w, h)
      draw()
    }

    updateCanvasSize()

    let lastTime = performance.now()
    const animate = (time) => {
      if (!grid) return
      const deltaTime = Math.min((time - lastTime) / 1000, 0.1)
      lastTime = time
      updateSquares(grid.squares, deltaTime)
      draw()
      frame = requestAnimationFrame(animate)
    }

    const resizeObserver = new ResizeObserver(updateCanvasSize)
    resizeObserver.observe(container)

    const intersectionObserver = new IntersectionObserver(
      ([entry]) => setIsInView(entry.isIntersecting),
      { threshold: 0 },
    )
    intersectionObserver.observe(canvas)

    if (isInView && !still) frame = requestAnimationFrame(animate)

    return () => {
      if (frame !== null) cancelAnimationFrame(frame)
      resizeObserver.disconnect()
      intersectionObserver.disconnect()
    }
  }, [setupCanvas, updateSquares, drawGrid, width, height, isInView])

  return (
    <div ref={containerRef} className={cn('h-full w-full', className)} {...props}>
      <canvas
        ref={canvasRef}
        className="pointer-events-none"
        style={{ verticalAlign: 'top', width: canvasSize.width, height: canvasSize.height }}
      />
    </div>
  )
}
