// Fichier : src/main/kotlin/com/cybedefend/toolwindow/panel/DetailsContainerPanel.kt
package com.cybedefend.toolwindow.panel

import GetProjectVulnerabilityByIdResponse
import VulnerabilitySca
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBLabel
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Font
import javax.swing.JPanel
import javax.swing.SwingConstants

/**
 * Conteneur qui gère l'affichage des différents panneaux de détails (SAST/IAC ou SCA) en utilisant
 * un CardLayout pour basculer entre les vues.
 */
class DetailsContainerPanel(project: Project) : JPanel(BorderLayout()) {
    private val cardLayout = CardLayout()

    // Les deux panneaux spécialisés
    private val sastIacDetailsPanel = VulnerabilitySastIacDetailsPanel(project)
    private val scaDetailsPanel = VulnerabilityScaDetailsPanel()
    private val emptyPanel =
            JBLabel("No vulnerability selected", SwingConstants.CENTER).apply {
                font = font.deriveFont(Font.ITALIC)
            }

    init {
        layout = cardLayout
        // Ajout des différentes "cartes"
        add(emptyPanel, "empty")
        add(sastIacDetailsPanel, "sast_iac")
        add(scaDetailsPanel, "sca")
        showPlaceholder()
    }

    /** Affiche la vue de départ. */
    fun showPlaceholder() {
        cardLayout.show(this, "empty")
    }

    /**
     * Affiche le panneau de détails pour les vulnérabilités SAST ou IAC.
     * @param dto La réponse complète de l'API.
     */
    fun showSastIacDetails(dto: GetProjectVulnerabilityByIdResponse) {
        sastIacDetailsPanel.showDetails(dto)
        cardLayout.show(this, "sast_iac")
    }

    /**
     * Affiche le panneau de détails pour une vulnérabilité SCA.
     * @param sca L'objet de vulnérabilité SCA.
     */
    fun showScaDetails(sca: VulnerabilitySca) {
        scaDetailsPanel.showDetails(sca)
        cardLayout.show(this, "sca")
    }
}
