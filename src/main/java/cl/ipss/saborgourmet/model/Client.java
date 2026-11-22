package cl.ipss.saborgourmet.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldNameConstants;

/**
 * Representa a un cliente registrado en el sistema con control de estado y cancelaciones tardías.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@FieldNameConstants
public class Client {

    private Long id;
    private String name;
    private String email;
    private String phone;
    private ClientStatus status;
    private int lateCancellations;

    public Client copy() {
        return toBuilder().build();
    }
}
