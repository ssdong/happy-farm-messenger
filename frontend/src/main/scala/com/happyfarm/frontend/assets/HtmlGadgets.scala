package com.happyfarm.frontend.assets

import com.raquo.laminar.api.L.*
import com.raquo.laminar.nodes.{ ReactiveHtmlElement, ReactiveSvgElement }
import org.scalajs.dom.{ HTMLButtonElement, HTMLDivElement, SVGSVGElement }

object HtmlGadgets:
  def loadingDots: HtmlElement =
    div(
      cls := "flex items-center justify-center space-x-2 py-4",
      styleTag(
        """
      @keyframes blink {
        0%, 20%   { opacity: 0.2; transform: scale(0.8); }
        40%       { opacity: 1; transform: scale(1.1); }
        100%      { opacity: 0.2; transform: scale(0.8); }
      }
      .dot {
        width: 10px;        /* Control actual width */
        height: 10px;       /* Control actual height */
        background-color: #94a3b8; /* Slate-400 */
        border-radius: 50%; /* Make them round */
        animation: blink 1.4s infinite;
        display: inline-block;
      }
      .dot:nth-child(1) { animation-delay: 0.0s; }
      .dot:nth-child(2) { animation-delay: 0.2s; }
      .dot:nth-child(3) { animation-delay: 0.4s; }
      """
      ),
      span(cls := "dot"),
      span(cls := "dot"),
      span(cls := "dot")
    )

  def errorView(text: String, needRefreshing: Boolean = true): ReactiveHtmlElement[HTMLDivElement] =
    div(
      cls := "w-full h-full flex items-center justify-center bg-gray-50",
      div(
        cls := "flex flex-col items-center space-y-2 text-center px-4",
        span(
          cls := "text-2xl md:text-3xl font-semibold text-gray-700",
          text
        ),
        if needRefreshing then
          span(
            cls := "text-lg md:text-xl text-gray-400 font-medium",
            "Try Refreshing"
          )
        else emptyNode
      )
    )

  def loadingView(text: String): ReactiveHtmlElement[HTMLDivElement] =
    div(
      cls := "w-full h-full flex items-center justify-center bg-gray-50",
      div(
        cls := "flex items-baseline space-x-1",
        span(cls := "text-2xl md:text-3xl font-semibold text-gray-700", text),
        // animated dots
        span(
          cls := "text-2xl md:text-3xl font-semibold text-gray-700",
          span(cls := "dot", "."),
          span(cls := "dot", "."),
          span(cls := "dot", ".")
        )
      ),
      styleTag("""
    @keyframes blink {
      0%, 20%   { opacity: 0; }
      40%       { opacity: 1; }
      100%      { opacity: 0; }
    }
    .dot {
      animation: blink 1.4s infinite;
      display: inline-block;
      line-height: 1;
    }
    .dot:nth-child(1) { animation-delay: 0.0s; }
    .dot:nth-child(2) { animation-delay: 0.2s; }
    .dot:nth-child(3) { animation-delay: 0.4s; }
    """)
    )

  def avatarNode(title: String, sizePx: Int = 48): HtmlElement =
    val initial = title.trim.headOption.map(_.toUpper).getOrElse('?')
    val hash    = (title.hashCode & 0x7fffffff) * 37

    // Deterministic HSL values
    val hue1 = hash        % 360
    val hue2 = (hue1 + 40) % 360 // Analogous color for the pattern
    val bg1  = s"hsl($hue1, 60%, 45%)"
    val bg2  = s"hsl($hue2, 70%, 35%)"

    div(
      cls := "rounded-full flex items-center justify-center text-white font-semibold shadow-inner",
      styleAttr :=
        s"""
        width: ${sizePx}px;
        height: ${sizePx}px;
        background-color: $bg1;
        background-image: repeating-linear-gradient(45deg, transparent, transparent 5px, $bg2 5px, $bg2 10px);
        font-size: ${(sizePx * 0.5).toInt}px;
        text-shadow: 1px 1px 2px rgba(0,0,0,0.5);
      """,
      initial.toString
    )

  def backButton: ReactiveHtmlElement[HTMLButtonElement] =
    button(
      cls := "absolute left-6 top-7 text-gray-400 hover:text-gray-600 focus:outline-none",
      // Back Arrow Icon (SVG)
      svg.svg(
        svg.cls         := "w-6 h-6",
        svg.fill        := "none",
        svg.viewBox     := "0 0 24 24",
        svg.strokeWidth := "2.5",
        svg.stroke      := "currentColor",
        svg.path(
          svg.strokeLineCap  := "round",
          svg.strokeLineJoin := "round",
          svg.d              := "M15.75 19.5L8.25 12l7.5-7.5"
        )
      )
    )

  def sendButton: ReactiveSvgElement[SVGSVGElement] =
    svg.svg(
      svg.cls     := "w-6 h-6",
      svg.viewBox := "0 0 16 16",
      svg.fill    := "currentColor",
      svg.path(
        // A Bootstrap send icon https://icons.getbootstrap.com/icons/send/
        svg.d := "M15.854.146a.5.5 0 0 1 .11.54l-5.819 14.547a.75.75 0 0 1-1.329.124l-3.178-4.995L.643 7.184a.75.75 0 0 1 .124-1.33L15.314.037a.5.5 0 0 1 .54.11ZM6.636 10.07l2.761 4.338L14.13 2.576zm6.787-8.201L1.591 6.602l4.339 2.76z"
      )
    )
