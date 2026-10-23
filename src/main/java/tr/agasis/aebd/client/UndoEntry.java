package tr.agasis.aebd.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import tr.agasis.aebd.common.DisplayData;
import tr.agasis.aebd.common.DisplayProps;
import tr.agasis.aebd.common.DisplayXform;

/**
 * One reversible editor action.
 *
 * <p>Undoing a deletion has to respawn the entities, which hands them fresh network ids. Rather than
 * invalidating every later history entry, the session keeps an old-id to new-id table and entries
 * resolve their ids through it on every use.
 */
public interface UndoEntry {

    void undo(EditorSession session);

    void redo(EditorSession session);

    final class Transform implements UndoEntry {
        private final Map<Integer, DisplayXform> before;
        private final Map<Integer, DisplayXform> after;

        Transform(Map<Integer, DisplayXform> before, Map<Integer, DisplayXform> after) {
            this.before = new LinkedHashMap<>(before);
            this.after = new LinkedHashMap<>(after);
        }

        @Override
        public void undo(EditorSession session) {
            session.sendXforms(session.resolveKeys(this.before));
            session.setSelection(session.resolveAll(this.before.keySet()));
        }

        @Override
        public void redo(EditorSession session) {
            session.sendXforms(session.resolveKeys(this.after));
            session.setSelection(session.resolveAll(this.after.keySet()));
        }
    }

    final class Properties implements UndoEntry {
        private final Map<Integer, DisplayProps> before;
        private final DisplayProps after;

        Properties(Map<Integer, DisplayProps> before, DisplayProps after) {
            this.before = new LinkedHashMap<>(before);
            this.after = after;
        }

        @Override
        public void undo(EditorSession session) {
            // Props are per-display, so restoring means one packet per distinct previous value.
            this.before.forEach((id, props) -> session.sendProps(List.of(session.resolve(id)), props));
            session.setSelection(session.resolveAll(this.before.keySet()));
        }

        @Override
        public void redo(EditorSession session) {
            List<Integer> ids = session.resolveAll(this.before.keySet());
            session.sendProps(ids, this.after);
            session.setSelection(ids);
        }
    }

    final class Create implements UndoEntry {
        private final List<DisplayData> data;
        private final List<Integer> ids;

        Create(List<DisplayData> data, List<Integer> ids) {
            this.data = List.copyOf(data);
            this.ids = new ArrayList<>(ids);
        }

        @Override
        public void undo(EditorSession session) {
            session.sendDelete(session.resolveAll(this.ids));
            session.clearSelection();
        }

        @Override
        public void redo(EditorSession session) {
            session.recreate(this.data, this.ids);
        }
    }

    final class Delete implements UndoEntry {
        private final List<DisplayData> data;
        private final List<Integer> ids;

        Delete(List<DisplayData> data, List<Integer> ids) {
            this.data = List.copyOf(data);
            this.ids = new ArrayList<>(ids);
        }

        @Override
        public void undo(EditorSession session) {
            session.recreate(this.data, this.ids);
        }

        @Override
        public void redo(EditorSession session) {
            session.sendDelete(session.resolveAll(this.ids));
            session.clearSelection();
        }
    }
}
