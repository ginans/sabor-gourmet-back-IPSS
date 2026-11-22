package cl.ipss.saborgourmet.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Representa una mesa física configurable del restaurante (número único, capacidad, ubicación y estado operativo).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class RestaurantTable {

    private Long id;
    private Integer number;
    private Integer capacity;
    private String location;
    private boolean active;

    public RestaurantTable copy() {
        return toBuilder().build();
    }
}
