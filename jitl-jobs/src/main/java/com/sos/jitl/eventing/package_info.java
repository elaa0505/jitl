package com.sos.jitl.eventing;

/**
 *
 */
/** @author KB */

// TODO <start_job> started auch jobs, die gestopped oder suspended sind.
// Abhilfe: start-job ausprogrammieren
// TODO Events im PostProcessing l�schen komt zu sp�t. Im actions.xml sofort ein
// Rename auf die Events und dann auch die renamed events l�schen
// TODO Process class erzeugen mit "30"
// TODO Events ohne Vorg�nger werden im postprocessing nicht gel�scht. sollten
// aber.
// TODO F�r die externen Events jeweils einen Job schreiben, der die setzt.
// TODO state-text f�r shell-scripte �ber eine Datei im monitor holen und dann
// setzen
// TODO JS Objete als Job, JC und Order erzeugen.
// TODO Jobs generieren als SSH-Jobs
// TODO JOC -> knopf, um eine Task-Queue komplett zu l�schen

// TODO eine "echte" Event-Queue implementieren. Im Moment sind die "Events" nur
// reine Statuse.
// TODO im actions.xml Variable erlauben (z.B. die LOADID). Dann l�uft ein
// event-handler z.B. f�r eine bestimmte LoadID
// in der Order ein Tooken mitgeben was dann als ID f�r die Variable verwendet
// werden kann (�hnlich ODAT z.B.).
// woher bekommt das Tooken seinen Wert? evtl. doch mit Job,JC und Order
// arbeiten

