/* 触屏手柄（issue #30/#35）：MV/MZ 共用。
 * 由 TyranoActivity 拼接进引擎 hook 注入；按键通过合成 keydown/keyup
 * （keyCode）派发，MV 与 MZ 的 Input 均读 keyCode，事件模型一致。
 *
 * 布局说明：所有尺寸/位置由 layout() 统一计算，锚定全视口
 * （window.innerWidth/innerHeight），portrait 模式下自然利用
 * letterbox 黑边空间；监听 resize/orientationchange 重排。
 * 布局完成后发布 window.__touchPadMetrics 并派发
 * tyranorpadlayout 事件，供修改器悬浮球避让动作键列。 */
window.addEventListener('load', () => {
  let padSize = 0
  let joyStickSR = 0
  let joyStickR = 0
  let joyStickCX = 0
  let joyStickCY = 0
  const allMargin = 10
  const lrMargin = 50
  let isKeysShown = true
  let useJoyStick = true
  let useDir8 = false
  const udlrEvents = {
    Up: false,
    Left: false,
    Right: false,
    Down: false
  }
  const joyStickStage = document.createElement('div')
  const joyStick = document.createElement('div')
  const actionsElement = document.createElement('div')
  const keySwitchElement = document.createElement('div')
  keySwitchElement.innerText = isKeysShown ? 'Hide' : 'Show'
  const joyStickSwitchElement = document.createElement('div')
  joyStickSwitchElement.innerText = useJoyStick ? 'Button' : 'Stick'
  const dir8SwitchElement = document.createElement('div')
  dir8SwitchElement.innerText = useDir8 ? '4 Dir' : '8 Dir'
  const udlrElement = document.createElement('div')
  const qwzxElement = document.createElement('div')
  document.body.appendChild(actionsElement)
  actionsElement.appendChild(keySwitchElement)
  actionsElement.appendChild(joyStickSwitchElement)
  actionsElement.appendChild(dir8SwitchElement)
  document.body.appendChild(qwzxElement)
  document.body.appendChild(joyStickStage)
  joyStickStage.appendChild(joyStick)
  document.body.appendChild(udlrElement)

  // issue #30：自定义布局。window.__touchPadConfig 由 TyranoActivity 按游戏注入：
  // { buttons: { <id>: { x, y, scale, visible } } }
  let padConfig = null
  try {
    const raw = window.__touchPadConfig
    if (raw && typeof raw === 'object' && raw.buttons) padConfig = raw
  } catch (e) { /* 忽略坏配置，回退默认 */ }
  const btnScale = (custom) => {
    const s = custom && custom.scale != null ? Number(custom.scale) : 1
    if (!isFinite(s) || s <= 0) return 1
    return Math.min(2, Math.max(0.01, s))
  }

  const keyCodes = {
    Tab: 9,
    Enter: 13,
    Shift: 16,
    Ctrl: 17,
    Alt: 18,
    Esc: 27,
    Space: 32,
    PageUp: 33,
    PageDown: 34,
    Left: 37,
    Up: 38,
    Right: 39,
    Down: 40,
    Q: 81,
    W: 87,
    X: 88,
    Z: 90
  }
  const actionsBtns = [
    { id: 'pageup', text: 'PageUp', keyCode: keyCodes.PageUp },
    { id: 'pagedown', text: 'PageDown', keyCode: keyCodes.PageDown },
    { id: 'tab', text: 'Tab', keyCode: keyCodes.Tab },
    { id: 'alt', text: 'Alt', keyCode: keyCodes.Alt },
    { id: 'ctrl', text: 'Ctrl', keyCode: keyCodes.Ctrl },
    { id: 'shift', text: 'Shift', keyCode: keyCodes.Shift },
    { id: 'space', text: 'Space', keyCode: keyCodes.Space },
    { id: 'enter', text: 'Enter', keyCode: keyCodes.Enter },
    { id: 'esc', text: 'Esc', keyCode: keyCodes.Esc }
  ]
  const udlrBtns = [
    {
      keyCodes: [keyCodes.Up],
      style: {
        transform: 'translate(-50%,0%) rotate(45deg)',
        borderTopLeftRadius: '50em',
        borderBottomLeftRadius: '50em',
        borderTopRightRadius: '50em',
        left: '50%',
        top: '0%',
        width: '40%',
        height: '40%'
      }
    },
    {
      keyCodes: [keyCodes.Left],
      style: {
        transform: 'translate(0%,-50%) rotate(45deg)',
        borderTopLeftRadius: '50em',
        borderBottomRightRadius: '50em',
        borderBottomLeftRadius: '50em',
        left: '0%',
        top: '50%',
        width: '40%',
        height: '40%'
      }
    },
    {
      keyCodes: [keyCodes.Right],
      style: {
        transform: 'translate(-100%,-50%) rotate(45deg)',
        borderTopRightRadius: '50em',
        borderBottomRightRadius: '50em',
        borderTopLeftRadius: '50em',
        left: '100%',
        top: '50%',
        width: '40%',
        height: '40%'
      }
    },
    {
      keyCodes: [keyCodes.Down],
      style: {
        transform: 'translate(-50%,-100%) rotate(45deg)',
        borderTopRightRadius: '50em',
        borderBottomLeftRadius: '50em',
        borderBottomRightRadius: '50em',
        left: '50%',
        top: '100%',
        width: '40%',
        height: '40%'
      }
    },
    {
      keyCodes: [keyCodes.Left, keyCodes.Up],
      style: {
        transform: 'translate(0%,0%)',
        borderBottomLeftRadius: '50em',
        borderTopLeftRadius: '50em',
        borderTopRightRadius: '50em',
        left: '0%',
        top: '0%',
        display: useDir8 ? 'block' : 'none'
      }
    },
    {
      keyCodes: [keyCodes.Left, keyCodes.Down],
      style: {
        transform: 'translate(0%,-100%)',
        borderTopLeftRadius: '50em',
        borderBottomLeftRadius: '50em',
        borderBottomRightRadius: '50em',
        left: '0%',
        top: '100%',
        display: useDir8 ? 'block' : 'none'
      }
    },
    {
      keyCodes: [keyCodes.Right, keyCodes.Up],
      style: {
        transform: 'translate(-100%,0%)',
        borderTopLeftRadius: '50em',
        borderTopRightRadius: '50em',
        borderBottomRightRadius: '50em',
        left: '100%',
        top: '0%',
        display: useDir8 ? 'block' : 'none'
      }
    },
    {
      keyCodes: [keyCodes.Right, keyCodes.Down],
      style: {
        transform: 'translate(-100%,-100%)',
        borderTopRightRadius: '50em',
        borderBottomLeftRadius: '50em',
        borderBottomRightRadius: '50em',
        left: '100%',
        top: '100%',
        display: useDir8 ? 'block' : 'none'
      }
    }
  ]
  const qwzxBtns = [
    { id: 'q', text: 'Q', keyCode: keyCodes.Q, style: { transform: 'translate(0%,-50%)', left: '0%', top: '50%' } },
    { id: 'w', text: 'W', keyCode: keyCodes.W, style: { transform: 'translate(-50%,0%)', left: '50%', top: '0%' } },
    { id: 'z', text: 'Z', keyCode: keyCodes.Z, style: { transform: 'translate(-50%,-100%)', left: '50%', top: '100%' } },
    { id: 'x', text: 'X', keyCode: keyCodes.X, style: { transform: 'translate(-100%,-50%)', left: '100%', top: '50%' } }
  ]
  const commonStyle = {
    position: 'absolute',
    zIndex: '99999999'
  }
  const btnStyle = {
    ...commonStyle,
    background: 'rgba(255,150,200,0.4)',
    color: 'rgba(255,255,255,0.3)',
    textAlign: 'center',
    boxShadow: '0 0 10px 0 rgba(255,255,255,0.5)'
  }
  const textStyle = {
    ...commonStyle,
    color: 'rgba(255,255,255,0.3)',
    transform: 'translate(-50%,-50%)',
    left: '50%',
    top: '50%'
  }
  const switchSize = () => `${padSize * 0.3}px`
  const actionBtnH = () => padSize * 0.125

  /* 全视口布局：始终使用整个屏幕，portrait 下按钮自然落入 letterbox 黑边。 */
  function gameRect() {
    return { rect: { left: 0, top: 0, width: window.innerWidth, height: window.innerHeight } }
  }

  let actionEls = []
  const qwzxEls = []
  function layout() {
    const g = gameRect()
    const r = g.rect
    padSize = Math.min(r.height * 0.4, r.width * 0.25)
    joyStickSR = padSize * 0.5
    joyStickR = joyStickSR * 0.4
    joyStickCX = r.left + joyStickSR + allMargin + lrMargin
    joyStickCY = r.top + r.height - joyStickSR - allMargin
    const switchTop = (i) => `${r.top + allMargin + i * (padSize * 0.3 + 5)}px`
    Object.assign(keySwitchElement.style, {
      ...btnStyle,
      width: switchSize(),
      height: switchSize(),
      lineHeight: switchSize(),
      borderRadius: '50em',
      left: `${r.left + allMargin}px`,
      top: switchTop(0),
      display: 'block'
    })
    Object.assign(joyStickSwitchElement.style, {
      ...btnStyle,
      width: switchSize(),
      height: switchSize(),
      lineHeight: switchSize(),
      borderRadius: '50em',
      left: `${r.left + allMargin}px`,
      top: switchTop(1),
      display: isKeysShown ? 'block' : 'none'
    })
    Object.assign(dir8SwitchElement.style, {
      ...btnStyle,
      width: switchSize(),
      height: switchSize(),
      lineHeight: switchSize(),
      borderRadius: '50em',
      left: `${r.left + allMargin}px`,
      top: switchTop(2),
      display: isKeysShown ? 'block' : 'none'
    })
    Object.assign(joyStickStage.style, {
      ...commonStyle,
      boxShadow: '0 0 10px 0 rgba(255,255,255,0.5)',
      width: `${padSize}px`,
      height: `${padSize}px`,
      transform: 'translate(0%,-100%)',
      borderRadius: '50em',
      left: `${r.left + allMargin + lrMargin}px`,
      top: `${joyStickCY + joyStickSR}px`,
      display: useJoyStick && isKeysShown ? 'block' : 'none'
    })
    Object.assign(joyStick.style, {
      ...btnStyle,
      marginLeft: `${joyStickSR - joyStickR}px`,
      marginTop: `${joyStickSR - joyStickR}px`,
      width: `${2 * joyStickR}px`,
      height: `${2 * joyStickR}px`,
      borderRadius: '50em'
    })
    Object.assign(udlrElement.style, {
      ...commonStyle,
      boxShadow: '0 0 10px 0 rgba(255,255,255,0.5)',
      borderRadius: '50em',
      width: `${padSize}px`,
      height: `${padSize}px`,
      transform: 'translate(0%,-100%)',
      left: `${r.left + allMargin + lrMargin}px`,
      top: `${joyStickCY + joyStickSR}px`,
      display: !useJoyStick && isKeysShown ? 'block' : 'none'
    })
    Object.assign(qwzxElement.style, {
      ...commonStyle,
      width: `${padSize}px`,
      height: `${padSize}px`,
      transform: 'translate(-100%,-100%)',
      borderRadius: '50em',
      boxShadow: '0 0 10px 0 rgba(255,255,255,0.5)',
      left: `${r.left + r.width - allMargin}px`,
      top: `${r.top + r.height - allMargin}px`,
      display: isKeysShown ? 'block' : 'none'
    })
    const btnW = padSize * 0.5
    const pitch = actionBtnH() + 5
    const activeCfg = (editMode ? editConfig : padConfig)
    actionEls.forEach((el, i) => {
      const meta = el.__pad || {}
      const custom = activeCfg && activeCfg.buttons && activeCfg.buttons[meta.id]
      const scale = btnScale(custom)
      if (custom && custom.visible === false) {
        el.style.display = 'none'
        return
      }
      Object.assign(el.style, {
        ...btnStyle,
        width: `${btnW}px`,
        height: `${actionBtnH()}px`,
        lineHeight: `${actionBtnH()}px`,
        borderRadius: '50em',
        right: 'auto',
        left: `${r.left + r.width - allMargin - btnW}px`,
        top: `${r.top + allMargin + i * pitch}px`,
        transform: scale === 1 ? '' : `scale(${scale})`,
        transformOrigin: 'center',
        display: isKeysShown ? 'block' : 'none'
      })
      if (custom && custom.x != null && custom.y != null) {
        const r0 = el.getBoundingClientRect()
        el.style.left = `${custom.x - r0.width / 2}px`
        el.style.top = `${custom.y - r0.height / 2}px`
      }
    })
    qwzxEls.forEach(el => {
      const meta = el.__pad || {}
      const activeCfgQ = (editMode ? editConfig : padConfig)
      const custom = activeCfgQ && activeCfgQ.buttons && activeCfgQ.buttons[meta.id]
      const scale = btnScale(custom)
      if (custom && custom.visible === false) {
        el.style.display = 'none'
        return
      }
      el.style.display = isKeysShown ? 'block' : 'none'
      if (custom && custom.x != null && custom.y != null) {
        const r0 = el.getBoundingClientRect()
        const pr = qwzxElement.getBoundingClientRect()
        el.style.transform = scale === 1 ? '' : `scale(${scale})`
        el.style.left = `${custom.x - r0.width / 2 - pr.left}px`
        el.style.top = `${custom.y - r0.height / 2 - pr.top}px`
        el.style.right = 'auto'
        el.style.transformOrigin = 'center'
      } else {
        el.style.transform = (scale === 1 ? '' : `scale(${scale})`) + (el.__baseTransform || '')
      }
    })
    // 发布动作键列区域，供修改器悬浮球避让（见 __rpgmaker_mod_ui.js）
    window.__touchPadMetrics = {
      actionLeft: r.left + r.width - allMargin - btnW,
      actionTop: r.top + allMargin,
      actionBottom: r.top + allMargin + actionEls.length * pitch - 5
    }
    window.dispatchEvent(new Event('tyranorpadlayout'))
  }

  const setKeyDownColor = (e) => {
    e.style.background = 'rgba(255,150,200,0.6)'
  }
  const setKeyUpColor = (e) => {
    e.style.background = 'rgba(255,150,200,0.4)'
  }
  const startKeyEvent = (e, keyCode, keyEvent) => {
    const evtObj = document.createEvent('UIEvents')
    Object.defineProperty(evtObj, 'keyCode', {
      get: () => {
        return evtObj.keyCodeVal
      }
    })
    Object.defineProperty(evtObj, 'which', {
      get: () => {
        return evtObj.keyCodeVal
      }
    })
    evtObj.initUIEvent(keyEvent, true, true, window, 1)
    evtObj.keyCodeVal = keyCode
    e.dispatchEvent(evtObj)
  }
  const setEventStart = (e, keyCodes) => {
    const press = (evt) => {
      if (editMode) return
      evt.stopPropagation()
      evt.preventDefault()
      setKeyDownColor(e)
      keyCodes.forEach(keyCode => {
        startKeyEvent(e, keyCode, 'keydown')
      })
    }
    // 触摸路径 preventDefault 会阻止浏览器合成鼠标事件，不会双触发；
    // 虚拟鼠标（__tyranor_mouse）只派发 MouseEvent，靠这里的 mousedown 命中
    e.addEventListener('touchstart', press)
    e.addEventListener('mousedown', press)
  }
  const setEventMove = (e) => {
    e.addEventListener('touchmove', (evt) => {
      evt.stopPropagation()
      evt.preventDefault()
    })
    e.addEventListener('mousemove', (evt) => { evt.stopPropagation() })
  }
  const setEventEnd = (e, keyCodes) => {
    const release = (evt) => {
      if (editMode) return
      evt.stopPropagation()
      evt.preventDefault()
      setKeyUpColor(e)
      keyCodes.forEach(keyCode => {
        startKeyEvent(e, keyCode, 'keyup')
      })
    }
    e.addEventListener('touchend', release)
    e.addEventListener('mouseup', release)
  }
  const getDistance = (x1, y1, x2, y2) => {
    return Math.sqrt((x1 - x2) * (x1 - x2) + (y1 - y2) * (y1 - y2))
  }
  const getAngle = (x1, y1, x2, y2) => {
    let angle = 180 * Math.atan((y1 - y2) / (x1 - x2)) / Math.PI
    if (x1 >= x2 && y1 < y2) angle += 360
    if (x1 < x2) angle += 180
    return angle
  }
  const endMoveEvent = () => {
    for (const key in udlrEvents) {
      if (udlrEvents[key]) {
        udlrEvents[key] = false
        startKeyEvent(joyStick, keyCodes[key], 'keyup')
      }
    }
  }
  const startMoveEvent = (touch) => {
    if (getDistance(touch.clientX, touch.clientY, joyStickCX, joyStickCY) > 20) {
      const angle = getAngle(touch.clientX, touch.clientY, joyStickCX, joyStickCY)
      const events = useDir8 ? {
        Up: angle > 202.5 && angle < 337.5,
        Right: (angle >= 0 && angle < 67.5) || (angle < 360 && angle > 292.5),
        Down: angle > 22.5 && angle < 157.5,
        Left: angle > 112.5 && angle < 247.5
      } : {
        Up: angle > 225 && angle < 315,
        Right: (angle >= 0 && angle < 45) || (angle < 360 && angle > 315),
        Down: angle > 45 && angle < 135,
        Left: angle > 135 && angle < 225
      }
      for (const key in events) {
        if (events[key] && !udlrEvents[key]) {
          udlrEvents[key] = true
          startKeyEvent(joyStick, keyCodes[key], 'keydown')
        }
        if (!events[key] && udlrEvents[key]) {
          udlrEvents[key] = false
          startKeyEvent(joyStick, keyCodes[key], 'keyup')
        }
      }
    } else {
      endMoveEvent()
    }
  }
  // 开关通用绑定：press/release 与按钮一致走 touchstart/touchend +
  // mousedown/mouseup 双路径（虚拟鼠标靠后者命中）
  const bindSwitch = (el, onRelease) => {
    el.addEventListener('touchstart', (evt) => {
      evt.stopPropagation()
      evt.preventDefault()
      setKeyDownColor(el)
    })
    el.addEventListener('mousedown', (evt) => {
      evt.stopPropagation()
      evt.preventDefault()
      setKeyDownColor(el)
    })
    setEventMove(el)
    const release = (evt) => {
      evt.stopPropagation()
      evt.preventDefault()
      setKeyUpColor(el)
      onRelease()
    }
    el.addEventListener('touchend', release)
    el.addEventListener('mouseup', release)
  }
  bindSwitch(keySwitchElement, () => {
    isKeysShown = !isKeysShown
    keySwitchElement.innerText = isKeysShown ? 'Hide' : 'Show'
    layout()
  })
  bindSwitch(joyStickSwitchElement, () => {
    useJoyStick = !useJoyStick
    joyStickSwitchElement.innerText = useJoyStick ? 'Button' : 'Stick'
    layout()
  })
  bindSwitch(dir8SwitchElement, () => {
    useDir8 = !useDir8
    dir8SwitchElement.innerText = useDir8 ? '4 Dir' : '8 Dir'
    for (let i = 4; i < udlrElement.children.length; i++) {
      udlrElement.children.item(i).style.display = useDir8 ? 'block' : 'none'
    }
  })
  const joyStart = (x, y) => {
    joyStick.style.left = `${x - joyStickCX}px`
    joyStick.style.top = `${y - joyStickCY}px`
    startMoveEvent({ clientX: x, clientY: y })
  }
  const joyMove = (x, y) => {
    const subLen = getDistance(x, y, joyStickCX, joyStickCY)
    if (subLen > joyStickSR) {
      joyStick.style.left = `${(x - joyStickCX) * joyStickSR / subLen}px`
      joyStick.style.top = `${(y - joyStickCY) * joyStickSR / subLen}px`
    } else {
      joyStick.style.left = `${x - joyStickCX}px`
      joyStick.style.top = `${y - joyStickCY}px`
    }
    startMoveEvent({ clientX: x, clientY: y })
  }
  const joyEnd = () => {
    joyStick.style.left = '0px'
    joyStick.style.top = '0px'
    endMoveEvent()
  }
  joyStickStage.addEventListener('touchstart', (evt) => {
    evt.stopPropagation()
    evt.preventDefault()
    const touch = evt.targetTouches[0]
    joyStart(touch.clientX, touch.clientY)
  })
  joyStickStage.addEventListener('touchmove', (evt) => {
    evt.stopPropagation()
    evt.preventDefault()
    const touch = evt.targetTouches[0]
    joyMove(touch.clientX, touch.clientY)
  })
  joyStickStage.addEventListener('touchend', (evt) => {
    evt.stopPropagation()
    evt.preventDefault()
    joyEnd()
  })
  // 虚拟鼠标拖拽摇杆：光标按下后跟随 mousemove
  let joyMouseDown = false
  joyStickStage.addEventListener('mousedown', (evt) => {
    evt.stopPropagation()
    evt.preventDefault()
    joyMouseDown = true
    joyStart(evt.clientX, evt.clientY)
  })
  window.addEventListener('mousemove', (evt) => {
    if (!joyMouseDown) return
    evt.stopPropagation()
    joyMove(evt.clientX, evt.clientY)
  })
  window.addEventListener('mouseup', (evt) => {
    if (!joyMouseDown) return
    evt.stopPropagation()
    joyMouseDown = false
    joyEnd()
  })
  actionsBtns.forEach(it => {
    const childElement = document.createElement('div')
    actionsElement.appendChild(childElement)
    childElement.innerText = it.text
    childElement.__pad = { id: it.id, text: it.text, defaultKeyCode: it.keyCode }
    actionEls.push(childElement)
    setEventStart(childElement, [it.keyCode])
    setEventMove(childElement)
    setEventEnd(childElement, [it.keyCode])
  })
  udlrBtns.forEach(it => {
    const childElement = document.createElement('div')
    udlrElement.appendChild(childElement)
    Object.assign(childElement.style, {
      ...btnStyle,
      width: '33%',
      height: '33%',
      ...it.style
    })
    setEventStart(childElement, it.keyCodes)
    setEventMove(childElement)
    setEventEnd(childElement, it.keyCodes)
  })
  qwzxBtns.forEach(it => {
    const childElement = document.createElement('div')
    qwzxElement.appendChild(childElement)
    Object.assign(childElement.style, {
      ...btnStyle,
      width: '40%',
      height: '40%',
      borderRadius: '50em',
      ...it.style
    })
    childElement.__pad = { id: it.id, text: it.text, defaultKeyCode: it.keyCode }
    childElement.__baseTransform = it.style.transform || ''
    qwzxEls.push(childElement)
    setEventStart(childElement, [it.keyCode])
    setEventMove(childElement)
    setEventEnd(childElement, [it.keyCode])
    const tElement = document.createElement('div')
    childElement.appendChild(tElement)
    Object.assign(tElement.style, textStyle)
    tElement.innerText = it.text
  })

  // ================= issue #30：游戏内按钮自定义（进入键盘映射） =================
  var editMode = false
  var selectedId = null
  var editConfig = null        // 编辑中的工作副本 { buttons: { id: {...} } }
  var overlay = null
  var panel = null
  var panelGrab = null         // 顶层控制框拖拽状态
  var NUDGE = 4
  var allEditableEls = []      // [{ el, id }]

  function collectEditable() {
    allEditableEls = []
    actionEls.forEach(el => { if (el.__pad) allEditableEls.push({ el: el, id: el.__pad.id }) })
    qwzxEls.forEach(el => { if (el.__pad) allEditableEls.push({ el: el, id: el.__pad.id }) })
  }

  function defaultButtonFor(id) {
    var found = actionsBtns.concat(qwzxBtns).filter(function (b) { return b.id === id })[0]
    return found && { x: null, y: null, scale: 1, visible: true, keyCode: found.keyCode, label: found.text }
  }

  function ensureButtonCfg(id) {
    if (!editConfig) editConfig = { buttons: {} }
    if (!editConfig.buttons[id]) editConfig.buttons[id] = defaultButtonFor(id)
    return editConfig.buttons[id]
  }

  function panelRect(el) {
    var r = el.getBoundingClientRect()
    return { cx: r.left + r.width / 2, cy: r.top + r.height / 2, w: r.width, h: r.height }
  }

  function styleButton(el, highlight) {
    var meta = el.__pad || {}
    var custom = editConfig && editConfig.buttons && editConfig.buttons[meta.id]
    var scale = btnScale(custom)
    el.classList.add('tm-pad-editable')
    el.style.outline = highlight ? '3px solid #fff' : ''
    el.style.zIndex = (highlight ? '100000002' : '100000001')
  }

  function refreshEditConfig() {
    collectEditable()
    allEditableEls.forEach(function (entry) {
      var meta = entry.el.__pad || {}
      var custom = editConfig && editConfig.buttons && editConfig.buttons[meta.id]
      var scale = btnScale(custom)
      if (custom && custom.visible === false) {
        entry.el.style.display = 'none'
        return
      }
      entry.el.style.display = 'block'
      if (custom && custom.x != null && custom.y != null) {
        var r0 = entry.el.getBoundingClientRect()
        entry.el.style.transform = scale === 1 ? '' : 'scale(' + scale + ')'
        entry.el.style.transformOrigin = 'center'
        if (entry.el.__group === 'qwzx') {
          var pr = qwzxElement.getBoundingClientRect()
          entry.el.style.left = (custom.x - r0.width / 2 - pr.left) + 'px'
          entry.el.style.top = (custom.y - r0.height / 2 - pr.top) + 'px'
        } else {
          entry.el.style.left = (custom.x - r0.width / 2) + 'px'
          entry.el.style.top = (custom.y - r0.height / 2) + 'px'
        }
        entry.el.style.right = 'auto'
      } else {
        // 复用 layout 的默认定位：先恢复列布局，再应用 scale
        entry.el.style.left = ''
        entry.el.style.top = ''
        entry.el.style.right = ''
        if (entry.el.__group === 'qwzx') {
          entry.el.style.transform = (scale === 1 ? '' : 'scale(' + scale + ')') + (entry.el.__baseTransform || '')
        } else {
          entry.el.style.transform = scale === 1 ? '' : 'scale(' + scale + ')'
        }
      }
      if (entry.el.__group !== 'qwzx') entry.el.style.display = 'block'
    })
  }

  function setSelected(id) {
    selectedId = id
    allEditableEls.forEach(function (entry) { styleButton(entry.el, entry.id === id) })
    var custom = editConfig.buttons && editConfig.buttons[id]
    var slider = panel && panel.querySelector('.tm-pad-scale')
    if (slider) slider.value = String(Math.round(btnScale(custom) * 100))
    var scaleLabel = panel && panel.querySelector('.tm-pad-scaleval')
    if (scaleLabel) scaleLabel.textContent = Math.round(btnScale(custom) * 100) + '%'
    var visLabel = panel && panel.querySelector('.tm-pad-vislabel')
    if (visLabel) visLabel.textContent = custom && custom.visible === false ? '（已隐藏）' : ''
  }

  function saveAndExit(force) {
    if (!editMode) return
    var small = allEditableEls.filter(function (entry) {
      var c = editConfig.buttons && editConfig.buttons[entry.id]
      return c && c.visible !== false && btnScale(c) < 0.5
    })
    var doSave = function () {
      padConfig = editConfig
      // 持久化到原生
      try {
        if (window && window.TyranorTouchPadNative && window.TyranorTouchPadNative.saveConfig) {
          window.TyranorTouchPadNative.saveConfig(JSON.stringify(padConfig))
        }
      } catch (e) { /* 忽略 */ }
      exitEdit()
      layout()
    }
    if (!force && small.length > 0) {
      if (window.confirm('有 ' + small.length + ' 个按钮缩放小于 50%，确认保存？')) doSave()
    } else {
      doSave()
    }
  }

  function buildPanel() {
    var style = document.getElementById('tm-pad-edit-css')
    if (!style) {
      style = document.createElement('style')
      style.id = 'tm-pad-edit-css'
      style.textContent =
        '.tm-pad-editable{cursor:move}' +
        '.tm-pad-btn{background:#3a3a4a;color:#fff;border:1px solid #666;border-radius:6px;padding:5px 10px;cursor:pointer;font:13px sans-serif}' +
        '.tm-pad-btn.primary{background:#2b6cb0}' +
        '.tm-pad-nudge{background:#3a3a4a;color:#fff;border:1px solid #666;border-radius:5px;width:26px;height:26px;cursor:pointer;font:14px sans-serif}'
      document.body.appendChild(style)
    }
    panel = document.createElement('div')
    panel.className = 'tm-pad-editpanel'
    panel.style.cssText = [
      'position:fixed', 'left:50%', 'top:10px', 'transform:translateX(-50%)',
      'z-index:100000003', 'background:rgba(20,20,30,0.92)', 'color:#fff',
      'border:1px solid #555', 'border-radius:12px', 'padding:10px 14px',
      'max-width:calc(100vw - 20px)', 'box-shadow:0 4px 20px rgba(0,0,0,0.5)',
      'user-select:none', 'font:14px/1.5 sans-serif', 'touch-action:none'
    ].join(';')
    panel.innerHTML =
      '<div class="tm-pad-title" style="font-weight:bold;margin-bottom:6px;cursor:move">键盘映射（拖动此框移动）</div>' +
      '<div style="display:flex;align-items:center;gap:8px;flex-wrap:wrap">' +
      '<label>缩放 <input type="range" min="1" max="200" value="100" class="tm-pad-scale" style="width:90px"></label>' +
      '<span class="tm-pad-scaleval">100%</span>' +
      '<button class="tm-pad-btn" data-act="transparent">透明</button>' +
      '<span class="tm-pad-vislabel" style="color:#ff9"></span>' +
      '</div>' +
      '<div style="display:flex;align-items:center;gap:6px;margin-top:8px;flex-wrap:wrap">' +
      '<span>微调</span>' +
      '<button class="tm-pad-nudge" data-d="up">↑</button>' +
      '<button class="tm-pad-nudge" data-d="left">←</button>' +
      '<button class="tm-pad-nudge" data-d="right">→</button>' +
      '<button class="tm-pad-nudge" data-d="down">↓</button>' +
      '</div>' +
      '<div style="display:flex;align-items:center;gap:8px;margin-top:10px;flex-wrap:wrap">' +
      '<button class="tm-pad-btn primary" data-act="save">保存并退出</button>' +
      '<button class="tm-pad-btn" data-act="reset">恢复默认</button>' +
      '<button class="tm-pad-btn" data-act="cancel">取消</button>' +
      '</div>'
    document.body.appendChild(panel)
    var title = panel.querySelector('.tm-pad-title')
    panel.addEventListener('pointerdown', function (ev) {
      if (ev.target !== title) return
      var cr = panel.getBoundingClientRect()
      panelGrab = { sx: ev.clientX, sy: ev.clientY, ox: cr.left, oy: cr.top }
    })
    panel.addEventListener('pointermove', function (ev) {
      if (!panelGrab) return
      panel.style.transform = 'none'
      panel.style.left = (panelGrab.ox + (ev.clientX - panelGrab.sx)) + 'px'
      panel.style.top = (panelGrab.oy + (ev.clientY - panelGrab.sy)) + 'px'
    })
    panel.addEventListener('pointerup', function () { panelGrab = null })
    panel.addEventListener('pointercancel', function () { panelGrab = null })

    var slider = panel.querySelector('.tm-pad-scale')
    slider.addEventListener('input', function () {
      if (!selectedId) return
      var c = ensureButtonCfg(selectedId)
      if (c.x == null && c.y == null) { var r = getElById(selectedId).getBoundingClientRect(); c.x = r.left + r.width / 2; c.y = r.top + r.height / 2 }
      c.scale = Number(slider.value) / 100
      refreshEditConfig()
      setSelected(selectedId)
    })
    panel.querySelectorAll('.tm-pad-nudge').forEach(function (b) {
      b.addEventListener('click', function () {
        if (!selectedId) return
        var c = ensureButtonCfg(selectedId)
        var el = getElById(selectedId)
        var pr = el.getBoundingClientRect()
        c.x = pr.left + pr.width / 2
        c.y = pr.top + pr.height / 2
        var d = b.getAttribute('data-d')
        if (d === 'up') c.y -= NUDGE
        else if (d === 'down') c.y += NUDGE
        else if (d === 'left') c.x -= NUDGE
        else if (d === 'right') c.x += NUDGE
        refreshEditConfig()
        setSelected(selectedId)
      })
    })
    panel.querySelector('[data-act="transparent"]').addEventListener('click', function () {
      if (!selectedId) return
      var c = ensureButtonCfg(selectedId)
      c.visible = false
      refreshEditConfig()
      setSelected(selectedId)
    })
    panel.querySelector('[data-act="save"]').addEventListener('click', function () { saveAndExit(false) })
    panel.querySelector('[data-act="reset"]').addEventListener('click', function () {
      collectEditable()
      allEditableEls.forEach(function (entry) {
        var meta = entry.el.__pad || {}
        if (editConfig.buttons) delete editConfig.buttons[meta.id]
      })
      refreshEditConfig()
      if (selectedId) setSelected(selectedId)
      layout()
    })
    panel.querySelector('[data-act="cancel"]').addEventListener('click', function () {
      editConfig = padConfig ? JSON.parse(JSON.stringify(padConfig)) : { buttons: {} }
      exitEdit()
      layout()
    })
  }

  function getElById(id) {
    for (var i = 0; i < allEditableEls.length; i++) if (allEditableEls[i].id === id) return allEditableEls[i].el
    return null
  }

  function enterEdit() {
    if (editMode) return
    editMode = true
    editConfig = padConfig ? JSON.parse(JSON.stringify(padConfig)) : { buttons: {} }
    overlay = document.createElement('div')
    overlay.style.cssText = 'position:fixed;left:0;top:0;width:100%;height:100%;' +
      'background:rgba(0,0,0,0.65);z-index:100000000;touch-action:none'
    document.body.appendChild(overlay)
    buildPanel()
    // 收集全部按钮并打上可编辑类
    collectEditable()
    allEditableEls.forEach(function (entry) {
      var el = entry.el
      el.__group = qwzxEls.indexOf(el) >= 0 ? 'qwzx' : 'action'
      el.classList.add('tm-pad-editable')
      el.style.pointerEvents = 'auto'
    })
    // 从已保存配置恢复工作副本位置（若首次编辑按默认布局落位）
    collectEditable()
    allEditableEls.forEach(function (entry) {
      var meta = entry.el.__pad || {}
      var c = editConfig.buttons[meta.id]
      if (!c || c.x == null || c.y == null) {
        var r = entry.el.getBoundingClientRect()
        editConfig.buttons[meta.id] = c || defaultButtonFor(meta.id)
        editConfig.buttons[meta.id].x = r.left + r.width / 2
        editConfig.buttons[meta.id].y = r.top + r.height / 2
      }
    })
    refreshEditConfig()
    // 选中第一个可见按钮方便直接调整
    var first = allEditableEls.filter(function (e) { var c = editConfig.buttons[e.id]; return !c || c.visible !== false })[0]
    if (first) setSelected(first.id)
    attachEditDrag()
  }

  function attachEditDrag() {
    var target = null
    var drag = null
    function onDown(ev, el) {
      ev.stopPropagation()
      ev.preventDefault()
      var meta = el.__pad || {}
      setSelected(meta.id)
      var c = ensureButtonCfg(meta.id)
      c.visible = true
      var r = el.getBoundingClientRect()
      c.x = r.left + r.width / 2
      c.y = r.top + r.height / 2
      drag = { id: meta.id, ox: ev.clientX, oy: ev.clientY, bx: c.x, by: c.y, moved: false }
    }
    function onMove(ev) {
      if (!drag) return
      var dx = ev.clientX - drag.ox
      var dy = ev.clientY - drag.oy
      if (!drag.moved && Math.abs(dx) + Math.abs(dy) < 8) return
      drag.moved = true
      var c = ensureButtonCfg(drag.id)
      c.x = drag.bx + dx
      c.y = drag.by + dy
      refreshEditConfig()
    }
    function onUp() { drag = null }

    allEditableEls.forEach(function (entry) {
      entry.el.removeEventListener('pointerdown', entry._pd)
      entry.el.removeEventListener('pointermove', entry._pm)
      entry.el.removeEventListener('pointerup', entry._pu)
      entry._pd = function (ev) { onDown(ev, entry.el) }
      entry._pm = onMove
      entry._pu = onUp
      entry.el.addEventListener('pointerdown', entry._pd)
      entry.el.addEventListener('pointermove', entry._pm)
      entry.el.addEventListener('pointerup', entry._pu)
    })
    if (overlay) {
      overlay.addEventListener('pointerup', onUp)
      overlay.addEventListener('pointercancel', onUp)
    }
  }

  function exitEdit() {
    editMode = false
    allEditableEls.forEach(function (entry) {
      if (entry.el._pd) entry.el.removeEventListener('pointerdown', entry._pd)
      if (entry.el._pm) entry.el.removeEventListener('pointermove', entry._pm)
      if (entry.el._pu) entry.el.removeEventListener('pointerup', entry._pu)
      delete entry.el._pd
      delete entry.el._pm
      delete entry.el._pu
      entry.el.classList.remove('tm-pad-editable')
      entry.el.style.outline = ''
    })
    if (overlay && overlay.parentNode) overlay.parentNode.removeChild(overlay)
    overlay = null
    if (panel && panel.parentNode) panel.parentNode.removeChild(panel)
    panel = null
    panelGrab = null
    selectedId = null
  }

  // 恢复默认布局：清空配置并持久化（可在编辑面板或从悬浮球触发）
  function resetToDefaults() {
    padConfig = { buttons: {} }
    if (editMode) editConfig = { buttons: {} }
    try {
      if (window && window.TyranorTouchPadNative && window.TyranorTouchPadNative.saveConfig) {
        window.TyranorTouchPadNative.saveConfig('{}')
      }
    } catch (e) { /* 忽略 */ }
    if (editMode) {
      collectEditable()
      refreshEditConfig()
      if (selectedId) setSelected(selectedId)
    }
    layout()
  }

  // 暴露 API 供修改器悬浮球（__rpgmaker_mod_ui.js）与外部调用
  window.__touchPad = {
    enterEdit: enterEdit,
    exitEdit: exitEdit,
    saveAndExit: saveAndExit,
    resetToDefaults: resetToDefaults,
    isEditMode: function () { return editMode },
    getConfig: function () { return padConfig ? JSON.stringify(padConfig) : '' }
  }

  layout()
  window.addEventListener('resize', layout)
  // 旋转后画布矩形可能滞后于视口变化，延迟一帧再排
  window.addEventListener('orientationchange', () => setTimeout(layout, 150))
})
