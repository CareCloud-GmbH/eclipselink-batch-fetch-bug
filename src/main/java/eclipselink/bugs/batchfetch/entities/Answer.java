package eclipselink.bugs.batchfetch.entities;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.HashSet;
import java.util.Set;


@Entity
@Table(name = "answers")
public class Answer {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id")
  private Long id;

  @Column(name = "text")
  private String text;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "assessment_id")
  private Assessment assessment;

  @ElementCollection
  @CollectionTable(
      name = "answers_tags",
      joinColumns = {@JoinColumn(name = "answer_id")})
  @Column(name = "tag")
  @Enumerated(EnumType.STRING)
  private Set<Tag> tags;

  protected Answer() {
    tags = new HashSet<>();
  }

  public Answer(Assessment assessment, String text) {
    this.assessment = assessment;
    this.text = text;
  }

  public Set<Tag> getTags() {
    return tags;
  }

}
