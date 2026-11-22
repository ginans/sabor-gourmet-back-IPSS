package cl.ipss.saborgourmet.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
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
