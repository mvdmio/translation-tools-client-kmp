package io.mvdm.translationtools.gradle

/** A resource pull wants to write for [key]; a null [resource] value means the remote value is null and the element goes. */
internal data class PulledChange(
   val key: String,
   val resource: ParsedAndroidResourceValue,
)

internal data class EditedStringsFile(
   val text: String,
   val warnings: List<String>,
)

private const val DEFAULT_INDENT = "   "
private const val RESOURCES_CLOSE_TAG = "</resources>"

private data class TextEdit(val start: Int, val end: Int, val replacement: String)

/**
 * A top-level element of `<resources>`: [start] to [end] is its whole range, [openEnd] the index of the `>` of its start
 * tag, and [closeStart] the start of its end tag (-1 for a self-closing element).
 */
private data class TopLevelElement(
   val tag: String,
   val name: String?,
   val start: Int,
   val end: Int,
   val openEnd: Int,
   val closeStart: Int,
)
{
   val selfClosing: Boolean get() = closeStart < 0
}

private data class StringsXmlLayout(
   val rootStart: Int,
   val rootOpenEnd: Int,
   val rootSelfClosing: Boolean,
   val closeStart: Int,
   val elements: List<TopLevelElement>,
)

private val nonElementMarkup = Regex("<!--.*?-->|<!\\[CDATA\\[.*?]]>|<\\?.*?\\?>", RegexOption.DOT_MATCHES_ALL)
private val nameAttribute = Regex("\\sname\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')")
private val childElementStart = Regex("<[A-Za-z]")
private val itemElementStart = Regex("<item[\\s>/]")

/**
 * Applies [changes] to the text of an existing `strings.xml` and touches nothing else: an element that changed is replaced,
 * one with a null value is removed, and one the file has no element for is added before `</resources>`. [text] is
 * well-formed, because the caller parsed it first.
 */
internal fun editStringsXml(text: String, origin: String, locale: String, changes: List<PulledChange>): EditedStringsFile
{
   val layout = readLayout(text)
   val elementsByName = layout.elements.filter { it.tag == "string" || it.tag == "plurals" }.associateBy { it.name }
   val eol = if (text.indexOf('\n') > 0 && text[text.indexOf('\n') - 1] == '\r') "\r\n" else "\n"
   val edits = mutableListOf<TextEdit>()
   val additions = mutableListOf<ParsedAndroidResourceValue>()
   val warnings = mutableListOf<String>()

   changes.forEach { (key, resource) ->
      val element = elementsByName[resource.resourceName]
      when {
         element == null -> if (resource.value != null) additions += resource
         resource.value == null -> edits += removal(text, element)
         else -> {
            if (element.tag == "string" && !element.selfClosing && hasChildElement(text.substring(element.openEnd + 1, element.closeStart)))
               warnings += "'$key' in '$origin' (locale $locale) held markup, which pull replaced with the plain text from TranslationTools."
            edits += TextEdit(element.start, element.end, replacement(text, element, resource, eol))
         }
      }
   }

   if (additions.isNotEmpty())
      edits += insertion(text, layout, additions.sortedBy { it.resourceName }, eol)

   val builder = StringBuilder(text)
   edits.sortedByDescending { it.start }.forEach { builder.replace(it.start, it.end, it.replacement) }
   return EditedStringsFile(builder.toString(), warnings)
}

/** The text of a `<string>` or `<plurals>` element, without the indentation of its first line. */
internal fun renderAndroidElement(resource: ParsedAndroidResourceValue, indent: String, eol: String): String
{
   val tag = if (resource.pluralItems == null) "string" else "plurals"
   val translatable = if (resource.managedRemotely) "" else " translatable=\"false\""
   return "<$tag name=\"${resource.resourceName}\"$translatable>${elementBody(resource, indent, indent + indent.ifEmpty { DEFAULT_INDENT }, eol)}</$tag>"
}

private fun elementBody(resource: ParsedAndroidResourceValue, indent: String, itemIndent: String, eol: String): String
{
   val items = resource.pluralItems ?: return escapeXml(resource.value.orEmpty())
   return items.joinToString("") { "$eol$itemIndent<item quantity=\"${it.quantity.keyword}\">${escapeXml(it.text)}</item>" } + eol + indent
}

internal fun escapeXml(value: String): String
{
   return value
      .replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
      .replace("\"", "&quot;")
      .replace("'", "&apos;")
}

private fun replacement(text: String, element: TopLevelElement, resource: ParsedAndroidResourceValue, eol: String): String
{
   val indent = lineIndent(text, element.start)
   val tag = if (resource.pluralItems == null) "string" else "plurals"
   if (element.tag != tag)
      return renderAndroidElement(resource, indent, eol)

   val startTag = text.substring(element.start, element.openEnd + 1)
   val openTag = if (element.selfClosing) startTag.removeSuffix("/>").trimEnd() + ">" else startTag
   val itemIndent = firstItemIndent(text, element) ?: (indent + indent.ifEmpty { DEFAULT_INDENT })
   return "$openTag${elementBody(resource, indent, itemIndent, eol)}</$tag>"
}

/** The leading whitespace of the element's first `<item>` line, when that item starts its own line. */
private fun firstItemIndent(text: String, element: TopLevelElement): String?
{
   if (element.selfClosing)
      return null

   val contentStart = element.openEnd + 1
   val item = itemElementStart.find(withoutNonElementMarkup(text.substring(contentStart, element.closeStart))) ?: return null
   val itemStart = contentStart + item.range.first
   val prefix = text.substring(text.lastIndexOf('\n', itemStart - 1) + 1, itemStart)
   return prefix.takeIf { it.isBlank() }
}

private fun removal(text: String, element: TopLevelElement): TextEdit
{
   val lineStart = text.lastIndexOf('\n', element.start - 1) + 1
   val lineBreak = text.indexOf('\n', element.end).let { if (it < 0) text.length else it }
   val alone = text.substring(lineStart, element.start).isBlank() && text.substring(element.end, lineBreak).isBlank()
   return if (alone) TextEdit(lineStart, minOf(lineBreak + 1, text.length), "") else TextEdit(element.start, element.end, "")
}

private fun insertion(text: String, layout: StringsXmlLayout, additions: List<ParsedAndroidResourceValue>, eol: String): TextEdit
{
   val indent = layout.elements.lastOrNull()?.let { lineIndent(text, it.start) } ?: DEFAULT_INDENT
   val lines = additions.joinToString("") { "$indent${renderAndroidElement(it, indent, eol)}$eol" }

   if (layout.rootSelfClosing) {
      val tagEnd = layout.rootStart + text.substring(layout.rootStart, layout.rootOpenEnd - 1).trimEnd().length
      return TextEdit(tagEnd, layout.rootOpenEnd + 1, ">$eol$lines$RESOURCES_CLOSE_TAG")
   }

   val closeLineStart = text.lastIndexOf('\n', layout.closeStart - 1) + 1
   return if (text.substring(closeLineStart, layout.closeStart).isBlank())
      TextEdit(closeLineStart, closeLineStart, lines)
   else
      TextEdit(layout.closeStart, layout.closeStart, eol + lines)
}

private fun lineIndent(text: String, position: Int): String
{
   val lineStart = text.lastIndexOf('\n', position - 1) + 1
   return text.substring(lineStart).takeWhile { it == ' ' || it == '\t' }
}

private fun hasChildElement(content: String): Boolean = childElementStart.containsMatchIn(withoutNonElementMarkup(content))

private fun withoutNonElementMarkup(content: String): String = nonElementMarkup.replace(content) { " ".repeat(it.value.length) }

private fun readLayout(text: String): StringsXmlLayout
{
   var rootStart = text.indexOf('<')
   while (true) {
      val afterMarkup = skipNonElementMarkup(text, rootStart) ?: break
      rootStart = text.indexOf('<', afterMarkup)
   }

   val rootOpenEnd = startTagEnd(text, rootStart)
   if (text[rootOpenEnd - 1] == '/')
      return StringsXmlLayout(rootStart, rootOpenEnd, true, -1, emptyList())

   val elements = mutableListOf<TopLevelElement>()
   var index = rootOpenEnd + 1
   while (true) {
      val start = text.indexOf('<', index)
      val afterMarkup = skipNonElementMarkup(text, start)
      if (afterMarkup != null) {
         index = afterMarkup
         continue
      }

      if (text.startsWith("</", start))
         return StringsXmlLayout(rootStart, rootOpenEnd, false, start, elements)

      val openEnd = startTagEnd(text, start)
      val openTag = text.substring(start, openEnd)
      val name = nameAttribute.find(openTag)?.groupValues?.let { it[1].ifEmpty { it[2] } }
      val end = elementEnd(text, start)
      val tag = openTag.drop(1).takeWhile { !it.isWhitespace() && it != '/' }
      elements += TopLevelElement(tag, name, start, end, openEnd, if (end == openEnd + 1) -1 else text.lastIndexOf("</", end))
      index = end
   }
}

/** The index after the comment, CDATA section or processing instruction at [start], or null when [start] holds none. */
private fun skipNonElementMarkup(text: String, start: Int): Int? = when {
   text.startsWith("<!--", start) -> text.indexOf("-->", start) + "-->".length
   text.startsWith("<![CDATA[", start) -> text.indexOf("]]>", start) + "]]>".length
   text.startsWith("<?", start) -> text.indexOf("?>", start) + "?>".length
   else -> null
}

/** The index of the `>` that ends the start tag at [start], past any `>` inside a quoted attribute value. */
private fun startTagEnd(text: String, start: Int): Int
{
   var quote: Char? = null
   for (index in start until text.length) {
      val char = text[index]
      when {
         quote != null -> if (char == quote) quote = null
         char == '"' || char == '\'' -> quote = char
         char == '>' -> return index
      }
   }

   throw IllegalStateException("Unterminated start tag at index $start.")
}

/** The index after the element at [start], with its nested elements, comments and CDATA sections. */
private fun elementEnd(text: String, start: Int): Int
{
   var depth = 0
   var index = start
   while (true) {
      val open = text.indexOf('<', index)
      val afterMarkup = skipNonElementMarkup(text, open)
      if (afterMarkup != null) {
         index = afterMarkup
         continue
      }

      if (text.startsWith("</", open)) {
         index = text.indexOf('>', open) + 1
         depth--
      }
      else {
         val tagEnd = startTagEnd(text, open)
         index = tagEnd + 1
         if (text[tagEnd - 1] != '/')
            depth++
      }

      if (depth == 0)
         return index
   }
}
