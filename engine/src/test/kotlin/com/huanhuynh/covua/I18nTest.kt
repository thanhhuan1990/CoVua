package com.huanhuynh.covua

import com.huanhuynh.covua.engine.Game
import com.huanhuynh.covua.engine.Level
import com.huanhuynh.covua.engine.Notation
import com.huanhuynh.covua.engine.Position
import com.huanhuynh.covua.i18n.I18n
import com.huanhuynh.covua.i18n.Lang
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class I18nTest {
    @After fun reset() { I18n.lang = Lang.VI }

    @Test fun knightMoveInAllLanguages() {
        val g = Game()
        val m = Notation.parse(g.pos, "Nf3")
        I18n.lang = Lang.VI; assertEquals("Mf3", Notation.show(g.pos, m)); assertEquals("Mã lên f3", Notation.spoken(g.pos, m))
        I18n.lang = Lang.EN; assertEquals("Nf3", Notation.show(g.pos, m)); assertEquals("Knight to f3", Notation.spoken(g.pos, m))
        I18n.lang = Lang.ZH; assertEquals("Nf3", Notation.show(g.pos, m)); assertEquals("马到f3", Notation.spoken(g.pos, m))
        I18n.lang = Lang.JA; assertEquals("Nf3", Notation.show(g.pos, m)); assertEquals("ナイトをf3へ", Notation.spoken(g.pos, m))
    }

    @Test fun internalCodesStayInternational() {
        I18n.lang = Lang.VI
        val g = Game()
        g.play(Notation.parse(g.pos, "Nf3"))
        assertEquals("Nf3", g.codes[0])
        assertEquals("Mf3", g.shown(0))
        assertEquals("O-O", Notation.localize("O-O"))
        assertEquals("Hxe7+", Notation.localize("Qxe7+"))
    }

    @Test fun levelTitlesTranslated() {
        for (l in Lang.entries) {
            I18n.lang = l
            for (lv in Level.entries) assertTrue(lv.title.isNotBlank() && lv.blurb.isNotBlank())
        }
        I18n.lang = Lang.JA; assertEquals("グランドマスター", Level.L8.title)
        assertEquals("キング", Notation.pieceName(Position.KING))
    }
}
