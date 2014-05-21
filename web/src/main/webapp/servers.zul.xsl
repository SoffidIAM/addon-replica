<?xml version="1.0" encoding="ISO-8859-1"?>
<xsl:stylesheet version="1.0"
        xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
        xmlns:zul="http://www.zkoss.org/2005/zul">

        <xsl:template match="row[@id='useMasterDatabaseRow']/@visible" priority="3">
                <xsl:attribute name="visible"><xsl:text>true</xsl:text></xsl:attribute>
        </xsl:template>

        <xsl:template match="row[@id='backupDatabaseRow']/@visible" priority="3">
                <xsl:attribute name="visible"><xsl:text>true</xsl:text></xsl:attribute>
        </xsl:template>

        <xsl:template match="node()|@*" priority="2">
                <xsl:copy>
                        <xsl:apply-templates select="node()|@*" />
                </xsl:copy>
        </xsl:template>

</xsl:stylesheet>
