<?xml version="1.0" encoding="UTF-8"?>
<xsl:transform version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
  <xsl:output method="text" version="1.0" encoding="UTF-8" indent="yes"/>
  <xsl:template match="/">
osgi.bundles=org.eclipse.osgi.services@start, \
<xsl:for-each select="//bundles/bundle">../dosgi_bundles/<xsl:value-of select="substring-after(text(), 'cxf-dosgi-ri-multibundle-distribution-1.0-SNAPSHOT.dir/dosgi_bundles/')"/><xsl:value-of select="string('@start, ')"/></xsl:for-each>
  </xsl:template>
</xsl:transform>
