package io.mvdm.translationtools.gradle

import java.io.ByteArrayInputStream
import java.io.IOException
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.helpers.DefaultHandler

/** One value of an XML property list, in the order the file holds it. */
internal sealed interface PlistNode

/** A `<dict>`: its key/value pairs in file order, duplicates and all. */
internal data class PlistDict(val entries: List<Pair<String, PlistNode>>) : PlistNode
{
   /** The pairs by key; a repeated key keeps its first position and its last value. */
   fun asMap(): Map<String, PlistNode> = entries.toMap()
}

internal data class PlistArray(val items: List<PlistNode>) : PlistNode

internal data class PlistString(val text: String) : PlistNode

internal data class PlistBool(val value: Boolean) : PlistNode

/** An element with text content that the plugin does not manage (`integer`, `real`, `date`, `data`), kept as written. */
internal data class PlistScalar(val tag: String, val text: String) : PlistNode

/**
 * The dictionary at the root of an XML property list, or null for a file that is binary, does not parse, or has any other
 * root. The Apple DOCTYPE every property list carries is allowed, but the parser never loads the DTD or an external entity.
 */
internal fun readXmlPropertyListDictionary(bytes: ByteArray): PlistDict?
{
   val factory = DocumentBuilderFactory.newInstance().apply {
      setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
      setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
      setFeature("http://xml.org/sax/features/external-general-entities", false)
      setFeature("http://xml.org/sax/features/external-parameter-entities", false)
      isXIncludeAware = false
   }

   // The default error handler prints a parse error to stderr; this one stays quiet and lets a fatal error throw.
   val builder = factory.newDocumentBuilder().apply { setErrorHandler(DefaultHandler()) }
   val document = try {
      builder.parse(InputSource(ByteArrayInputStream(bytes)))
   }
   catch (exception: SAXException) {
      return null
   }
   catch (exception: IOException) {
      return null
   }

   val plist = document.documentElement.takeIf { it.tagName == "plist" } ?: return null
   return plist.childElements().firstOrNull()?.takeIf { it.tagName == "dict" }?.toPlistDict()
}

private fun Element.toPlistNode(): PlistNode = when (tagName) {
   "dict" -> toPlistDict()
   "array" -> PlistArray(childElements().map { it.toPlistNode() })
   "true" -> PlistBool(true)
   "false" -> PlistBool(false)
   "string" -> PlistString(textContent)
   else -> PlistScalar(tagName, textContent)
}

/** The pairs of a `<dict>`; a stray element that is not a `<key>` or has no value after it is ignored. */
private fun Element.toPlistDict(): PlistDict
{
   val children = childElements()
   val pairs = mutableListOf<Pair<String, PlistNode>>()
   var index = 0
   while (index + 1 < children.size) {
      if (children[index].tagName == "key") {
         pairs += children[index].textContent to children[index + 1].toPlistNode()
         index += 2
      }
      else {
         index += 1
      }
   }

   return PlistDict(pairs)
}

private fun Element.childElements(): List<Element> =
   (0 until childNodes.length).map { childNodes.item(it) }.filterIsInstance<Element>()

private const val PLIST_HEADER =
   "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
      "<!DOCTYPE plist PUBLIC \"-//Apple//DTD PLIST 1.0//EN\" \"http://www.apple.com/DTDs/PropertyList-1.0.dtd\">\n" +
      "<plist version=\"1.0\">\n"

/**
 * [root] as an XML property list the way Xcode writes it: the XML declaration, the Apple DOCTYPE, tab indentation, empty
 * containers as `<dict/>` and `<array/>`, and a trailing newline. UTF-8 without a byte order mark.
 */
internal fun serializePropertyList(root: PlistDict): ByteArray
{
   val builder = StringBuilder(PLIST_HEADER)
   appendPlistNode(builder, root, 0)
   builder.append("</plist>\n")
   return builder.toString().toByteArray(Charsets.UTF_8)
}

private fun appendPlistNode(builder: StringBuilder, node: PlistNode, depth: Int)
{
   val indent = "\t".repeat(depth)
   when (node) {
      is PlistDict -> appendContainer(builder, "dict", indent, node.entries.isEmpty()) {
         node.entries.forEach { (key, value) ->
            builder.append(indent).append("\t<key>").append(escapePlistText(key)).append("</key>\n")
            appendPlistNode(builder, value, depth + 1)
         }
      }

      is PlistArray -> appendContainer(builder, "array", indent, node.items.isEmpty()) {
         node.items.forEach { appendPlistNode(builder, it, depth + 1) }
      }

      is PlistString -> builder.append(indent).append("<string>").append(escapePlistText(node.text)).append("</string>\n")
      is PlistBool -> builder.append(indent).append(if (node.value) "<true/>\n" else "<false/>\n")
      is PlistScalar -> builder.append(indent).append('<').append(node.tag).append('>').append(escapePlistText(node.text))
         .append("</").append(node.tag).append(">\n")
   }
}

private fun appendContainer(builder: StringBuilder, tag: String, indent: String, isEmpty: Boolean, appendChildren: () -> Unit)
{
   if (isEmpty) {
      builder.append(indent).append('<').append(tag).append("/>\n")
      return
   }

   builder.append(indent).append('<').append(tag).append(">\n")
   appendChildren()
   builder.append(indent).append("</").append(tag).append(">\n")
}

private fun escapePlistText(text: String): String =
   text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
