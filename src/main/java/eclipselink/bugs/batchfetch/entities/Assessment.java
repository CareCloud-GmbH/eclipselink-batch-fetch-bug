package eclipselink.bugs.batchfetch.entities;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MapKey;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.StringJoiner;


@Entity
@Table(name = "assessments")
public class Assessment {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id")
  private Long id;

  @Column(name = "name")
  private String name;

  @OneToMany(mappedBy = "assessment", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
  @MapKey(name = "id")
  private Map<Long, Answer> answers;

  public Assessment() {
    answers = new HashMap<>();
  }

  public Assessment(String name) {
    this.name = name;
    answers = new HashMap<>();
  }

  public Collection<Answer> getAnswers() {
    return answers.values();
  }

  @Override
  public String toString() {
    return new StringJoiner(", ", Assessment.class.getSimpleName() + "[", "]")
        .add("id=" + id).toString();
  }
}
