import { Pressable, StyleSheet, Text, View } from 'react-native';

import { categoryLabel, statusLabel } from './labels';
import { COLORS } from './theme';

// Summary of the incident selected on the map. It is a plain React Native view on
// top of the map, not a react-native-maps <Callout>: on Android a custom callout
// reuses one native view and crashes the app when it is shown again
// ("The specified child already has a parent").
export function IncidentCard({ incident, onOpen, onClose, style }) {
  return (
    <Pressable
      style={({ pressed }) => [styles.card, pressed && styles.cardPressed, style]}
      onPress={onOpen}
      accessibilityRole="button"
      accessibilityHint="Abre el detalle de la incidencia"
    >
      <View style={styles.header}>
        <Text style={styles.title} numberOfLines={2}>
          {incident.title}
        </Text>
        <Pressable
          style={styles.closeButton}
          onPress={onClose}
          hitSlop={12}
          accessibilityRole="button"
          accessibilityLabel="Cerrar"
        >
          <Text style={styles.closeButtonText}>×</Text>
        </Pressable>
      </View>

      <View style={styles.tags}>
        <Text style={[styles.tag, styles.categoryTag]}>{categoryLabel(incident.categoryName)}</Text>
        <Text style={[styles.tag, styles.statusTag]}>{statusLabel(incident.status)}</Text>
      </View>

      <Text style={styles.link}>Ver detalles ›</Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  card: {
    padding: 16,
    borderRadius: 16,
    backgroundColor: COLORS.surface,
    elevation: 6,
    shadowColor: '#000',
    shadowOpacity: 0.2,
    shadowRadius: 8,
    shadowOffset: { width: 0, height: 3 },
  },
  cardPressed: {
    backgroundColor: COLORS.surfaceMuted,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: 12,
  },
  title: {
    flex: 1,
    fontSize: 17,
    fontWeight: '700',
    color: COLORS.text,
  },
  closeButton: {
    width: 28,
    height: 28,
    borderRadius: 14,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: COLORS.surfaceMuted,
  },
  closeButtonText: {
    fontSize: 18,
    lineHeight: 20,
    color: COLORS.textMuted,
  },
  tags: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
    marginTop: 10,
  },
  tag: {
    paddingHorizontal: 10,
    paddingVertical: 4,
    borderRadius: 12,
    overflow: 'hidden',
    fontSize: 13,
    fontWeight: '600',
  },
  categoryTag: {
    backgroundColor: COLORS.surfaceMuted,
    color: COLORS.text,
  },
  statusTag: {
    backgroundColor: COLORS.primarySoft,
    color: COLORS.primary,
  },
  link: {
    marginTop: 12,
    alignSelf: 'flex-end',
    fontSize: 15,
    fontWeight: '600',
    color: COLORS.primary,
  },
});
