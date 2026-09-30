package me.blueslime.meteor.paper.extras.menus.definition;

import me.blueslime.meteor.paper.extras.actions.runtime.ActionPlan;
import me.blueslime.meteor.paper.extras.conditions.runtime.ConditionPlan;
import me.blueslime.meteor.paper.extras.interaction.InteractiveItemDefinition;

import java.util.*;

public final class MenuDefinition {

    private final String id;

    private final int size;

    private final String titleTemplate;

    private final Map<
            String,
            InteractiveItemDefinition
            > items;

    private final List<
            InteractiveItemDefinition
            > orderedItems;

    private final ConditionPlan openConditions;

    private final ActionPlan openActions;

    private final ActionPlan deniedOpenActions;

    private final ActionPlan closeActions;

    private final MenuPolicy policy;

    public MenuDefinition(
            String id,
            int size,
            String titleTemplate,
            Map<String, InteractiveItemDefinition> items,
            ConditionPlan openConditions,
            ActionPlan openActions,
            ActionPlan deniedOpenActions,
            ActionPlan closeActions,
            MenuPolicy policy
    ) {
        if (
                id == null ||
                        id.isBlank()
        ) {
            throw new IllegalArgumentException(
                    "Menu id cannot be empty"
            );
        }

        validateSize(size);

        this.id =
                normalize(id);

        this.size =
                size;

        this.titleTemplate =
                titleTemplate == null
                        ? id
                        : titleTemplate;

        LinkedHashMap<
                String,
                InteractiveItemDefinition
                > copy =
                new LinkedHashMap<>();

        if (items != null) {
            items.forEach(
                    (itemId, definition) -> {
                        if (
                                itemId == null ||
                                        definition == null
                        ) {
                            return;
                        }

                        copy.put(
                                normalize(itemId),
                                definition
                        );
                    }
            );
        }

        this.items =
                Collections.unmodifiableMap(
                        copy
                );

        this.orderedItems =
                List.copyOf(
                        copy.values()
                );

        this.openConditions =
                Objects.requireNonNullElse(
                        openConditions,
                        ConditionPlan.EMPTY
                );

        this.openActions =
                Objects.requireNonNullElse(
                        openActions,
                        ActionPlan.EMPTY
                );

        this.deniedOpenActions =
                Objects.requireNonNullElse(
                        deniedOpenActions,
                        ActionPlan.EMPTY
                );

        this.closeActions =
                Objects.requireNonNullElse(
                        closeActions,
                        ActionPlan.EMPTY
                );

        this.policy =
                Objects.requireNonNullElseGet(
                        policy,
                        MenuPolicy::protectedMenu
                );
    }

    public String id() {
        return id;
    }

    public int size() {
        return size;
    }

    public String titleTemplate() {
        return titleTemplate;
    }

    public Map<
            String,
            InteractiveItemDefinition
            > items() {
        return items;
    }

    public List<
            InteractiveItemDefinition
            > orderedItems() {
        return orderedItems;
    }

    public Optional<InteractiveItemDefinition> item(
            String id
    ) {
        if (id == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(
                items.get(
                        normalize(id)
                )
        );
    }

    public ConditionPlan openConditions() {
        return openConditions;
    }

    public ActionPlan openActions() {
        return openActions;
    }

    public ActionPlan deniedOpenActions() {
        return deniedOpenActions;
    }

    public ActionPlan closeActions() {
        return closeActions;
    }

    public MenuPolicy policy() {
        return policy;
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    private void validateSize(
            int size
    ) {
        if (
            size < 9 ||
            size > 54 ||
            size % 9 != 0
        ) {
            throw new IllegalArgumentException(
                    "Menu size must be a multiple of 9 between 9 and 54, got "
                            + size
            );
        }
    }

    private String normalize(
            String input
    ) {
        return input
                .strip()
                .toLowerCase(
                        Locale.ROOT
                );
    }
}
